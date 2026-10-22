package com.themis.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.themis.mobile.data.ActivityEntity
import com.themis.mobile.tracking.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A finished workout plus its route (Douglas-Peucker reduced: for drawing and sharing only). */
data class WorkoutSummary(val activity: ActivityEntity, val route: List<GeoPoint>) {
    /** Average pace in seconds per km, on moving time when we have it. Null if the distance is too short. */
    val avgPaceSecPerKm: Double?
        get() {
            val ms = if (activity.movingTimeMs > 0) activity.movingTimeMs else activity.elapsedMs
            return if (activity.distanceMeters < 10) null else (ms / 1000.0) / (activity.distanceMeters / 1000.0)
        }
}

/** Only reads state and sends commands. All location and sensor work lives in [TrackingForegroundService]. */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    app: Application,
    private val repo: ActivityRepository,
    private val store: TrackingStore
) : AndroidViewModel(app) {
    val state: StateFlow<TrackingState> = store.state
    val history: StateFlow<List<ActivityEntity>> =
        repo.observeFinished().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _summary = MutableStateFlow<WorkoutSummary?>(null)
    val summary: StateFlow<WorkoutSummary?> = _summary.asStateFlow()

    init {
        // A workout can outlive the process; if the service isn't running it yet, offer to resume it.
        viewModelScope.launch {
            if (store.state.value.status == TrackingStatus.IDLE && repo.unfinished() != null)
                store.update { it.copy(hasUnfinished = true) }
        }
        // Done was tapped (in the app or from the notification): open the summary for the saved workout.
        viewModelScope.launch {
            store.state.map { it.finishedActivityId }.filterNotNull().distinctUntilChanged().collect { id ->
                store.update { it.copy(finishedActivityId = null) }
                openSummary(id)
            }
        }
    }

    private val ctx get() = getApplication<Application>()
    fun start() { TrackingController.start(ctx) }
    fun pause() { TrackingController.pause(ctx) }
    fun resume() { TrackingController.resume(ctx) }
    fun stop() { TrackingController.stop(ctx) }
    fun restore() { TrackingController.restore(ctx) }

    fun openSummary(id: Long) {
        viewModelScope.launch {
            val a = repo.get(id) ?: return@launch
            val pts = repo.loadPoints(id).map { GeoPoint(it.latitude, it.longitude) }
            _summary.value = WorkoutSummary(a, withContext(Dispatchers.Default) { DouglasPeucker.simplify(pts, 3.0) })
        }
    }

    fun closeSummary() { _summary.value = null }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            if (_summary.value?.activity?.id == id) _summary.value = null
        }
    }
}
