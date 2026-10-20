package com.themis.mobile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

const val KG_PER_LB = 0.45359237
const val EPOCH = "1970-01-01T00:00:00Z"
private val TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
fun nowIso(): String = TS.format(Instant.now())

class Repository(private val db: ThemisDatabase) {
    val tasks: Flow<List<TaskEntity>> = db.taskDao().observeActive()
    val completions: Flow<List<CompletionEntity>> = db.completionDao().observeActive()
    val weights: Flow<List<WeightEntity>> = db.weightDao().observeActive()
    val unit: Flow<String> = db.settingDao().observe("unit").map { if (it?.value == "lb") "lb" else "kg" }

    /** Each mutator returns an error message, or null on success (same rules as the desktop app). */
    suspend fun addTask(title: String, description: String, oneOff: Boolean = false): String? {
        val t = title.trim()
        if (t.isEmpty()) return "A task needs a title."
        db.taskDao().insert(
            TaskEntity(title = t, description = description.trim(),
                createdAt = LocalDate.now().toString(), updatedAt = nowIso(), isOneOff = oneOff)
        )
        return null
    }

    suspend fun deleteTask(id: Long) {
        val t = db.taskDao().byId(id) ?: return
        db.taskDao().update(t.copy(isDeleted = true, updatedAt = nowIso()))   // soft delete
    }

    suspend fun setCompletion(taskId: Long, day: LocalDate, done: Boolean): String? {
        if (day.isAfter(LocalDate.now())) return "You can't tick off a future day."
        val t = db.taskDao().byId(taskId)
        if (t == null || t.isDeleted || t.isOneOff || day.toString() < t.createdAt) return "This task didn't exist on that day."
        db.completionDao().upsert(CompletionEntity(taskId, day.toString(), nowIso(), isDeleted = !done))
        return null
    }

    /** Tick (or untick) a one-off task. Stored on the task itself so it syncs like any other edit. */
    suspend fun setOneOffDone(id: Long, done: Boolean) {
        val t = db.taskDao().byId(id) ?: return
        if (!t.isOneOff || t.isDeleted) return
        db.taskDao().update(t.copy(isDone = done, updatedAt = nowIso()))
    }

    suspend fun setUnit(unit: String) {
        if (unit == "kg" || unit == "lb") db.settingDao().upsert(SettingEntity("unit", unit, nowIso()))
    }

    suspend fun setWeight(day: LocalDate, value: Double?, unit: String): String? {
        if (value == null) return "Enter a valid date and weight."
        if (day.isAfter(LocalDate.now())) return "You can't log a weight for a future day."
        val kg = if (unit == "lb") value * KG_PER_LB else value
        if (kg < 20 || kg > 500) return "That weight looks out of range."
        db.weightDao().upsert(WeightEntity(day.toString(), kg, nowIso()))
        return null
    }

    suspend fun deleteWeight(day: LocalDate) {
        val w = db.weightDao().get(day.toString()) ?: return
        db.weightDao().upsert(w.copy(isDeleted = true, updatedAt = nowIso()))
    }
}
