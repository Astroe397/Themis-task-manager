package com.themis.mobile.tracking

import com.themis.mobile.data.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(private val db: ThemisDatabase) {
    private val activities = db.activityDao()
    private val points = db.trackPointDao()

    fun observeFinished(): Flow<List<ActivityEntity>> = activities.observeFinished()
    suspend fun unfinished() = activities.unfinished()
    suspend fun get(id: Long) = activities.byId(id)

    suspend fun create(startedAt: Long): Long =
        activities.insert(ActivityEntity(startedAt = startedAt, status = "RECORDING"))

    suspend fun addPoint(p: TrackPointEntity) = points.insert(p)
    suspend fun loadPoints(id: Long): List<TrackPointEntity> = points.forActivity(id)

    suspend fun saveProgress(id: Long, distance: Double, elapsed: Long, moving: Long, steps: Int, s: StepCounterState) =
        activities.saveProgress(id, distance, elapsed, moving, steps, s.baseline, s.offset, s.last)

    suspend fun setStatus(id: Long, status: String, endedAt: Long? = null) = activities.setStatus(id, status, endedAt)
    suspend fun delete(id: Long) = activities.delete(id)   // track points cascade
}
