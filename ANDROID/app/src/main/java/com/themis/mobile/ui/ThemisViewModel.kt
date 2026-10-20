package com.themis.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.themis.mobile.ThemisApp
import com.themis.mobile.data.*
import com.themis.mobile.reminder.WaterReminder
import com.themis.mobile.sync.Quote
import com.themis.mobile.sync.QuoteClient
import com.themis.mobile.sync.SyncResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SyncState { Searching, Synced, Offline }
data class SyncUi(val state: SyncState, val label: String)
data class OneOffTask(val id: Long, val title: String, val description: String, val done: Boolean)
data class DayTask(val id: Long, val title: String, val description: String, val done: Boolean)

class ThemisViewModel(app: Application) : AndroidViewModel(app) {
    private val themis = app as ThemisApp
    private val repo = themis.repo
    private val share = SharingStarted.WhileSubscribed(5000)

    val today = MutableStateFlow(LocalDate.now())
    val selectedDay = MutableStateFlow(LocalDate.now())

    private val doneByDay = combine(repo.tasks, repo.completions) { tasks, comps ->
        val ids = tasks.filter { !it.isOneOff }.map { it.id }.toSet()   // ignore deleted and one-off tasks
        comps.filter { it.taskId in ids }.groupingBy { LocalDate.parse(it.day) }.eachCount()
    }

    val stats: StateFlow<Stats?> = combine(repo.tasks, doneByDay, today) { tasks, done, t ->
        computeStats(tasks.filter { !it.isOneOff }.map { LocalDate.parse(it.createdAt) }, done, t)
    }.stateIn(viewModelScope, share, null)

    val dayTasks: StateFlow<List<DayTask>> = combine(repo.tasks, repo.completions, selectedDay) { tasks, comps, day ->
        val doneIds = comps.filter { it.day == day.toString() }.map { it.taskId }.toSet()
        tasks.filter { !it.isOneOff && it.createdAt <= day.toString() }.map { DayTask(it.id, it.title, it.description, it.id in doneIds) }
    }.stateIn(viewModelScope, share, emptyList())

    val oneOffs: StateFlow<List<OneOffTask>> = repo.tasks.map { tasks ->
        val all = tasks.filter { it.isOneOff }
        // open ones oldest-first, finished ones most-recently-finished first
        (all.filter { !it.isDone }.sortedBy { it.id } + all.filter { it.isDone }.sortedByDescending { it.updatedAt })
            .map { OneOffTask(it.id, it.title, it.description, it.isDone) }
    }.stateIn(viewModelScope, share, emptyList())

    val weight: StateFlow<WeightData?> = combine(repo.weights, repo.unit, today) { w, u, t ->
        buildWeightData(w, u, t)
    }.stateIn(viewModelScope, share, null)

    val quote = MutableStateFlow<Quote?>(null)
    val waterOn = MutableStateFlow(WaterReminder.isEnabled(app))
    val sync = MutableStateFlow(SyncUi(SyncState.Offline, "Not synced yet"))
    private var syncJob: Job? = null

    init { viewModelScope.launch { quote.value = QuoteClient.fetch() } }

    fun refreshToday() { today.value = LocalDate.now() }
    fun selectDay(d: LocalDate) { selectedDay.value = d }

    fun syncNow() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            sync.value = SyncUi(SyncState.Searching, "Searching…")
            sync.value = when (val r = themis.syncEngine.sync()) {
                is SyncResult.Synced -> SyncUi(SyncState.Synced, "Connected & synced")
                SyncResult.NotFound -> SyncUi(SyncState.Offline, "Desktop not found")
                is SyncResult.Failed -> SyncUi(SyncState.Offline, "Sync failed")
            }
        }
    }

    fun addTask(title: String, desc: String, oneOff: Boolean, onResult: (String?) -> Unit) =
        viewModelScope.launch { onResult(repo.addTask(title, desc, oneOff)) }
    fun setOneOffDone(id: Long, done: Boolean) = viewModelScope.launch { repo.setOneOffDone(id, done) }
    fun setWater(on: Boolean) { WaterReminder.setEnabled(getApplication(), on); waterOn.value = on }
    fun deleteTask(id: Long) = viewModelScope.launch { repo.deleteTask(id) }
    fun setDone(id: Long, day: LocalDate, done: Boolean, onError: (String) -> Unit) =
        viewModelScope.launch { repo.setCompletion(id, day, done)?.let(onError) }
    fun setUnit(u: String) = viewModelScope.launch { repo.setUnit(u) }
    fun saveWeight(day: LocalDate, value: Double?, unit: String, onResult: (String?) -> Unit) =
        viewModelScope.launch { onResult(repo.setWeight(day, value, unit)) }
    fun removeWeight(day: LocalDate) = viewModelScope.launch { repo.deleteWeight(day) }
}
