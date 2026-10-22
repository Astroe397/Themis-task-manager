package com.themis.mobile.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One recorded workout. Progress columns are rewritten on every GPS fix so a killed process loses nothing. */
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,                 // epoch ms
    val endedAt: Long? = null,
    val status: String,                  // RECORDING | PAUSED | FINISHED
    val distanceMeters: Double = 0.0,
    val elapsedMs: Long = 0,             // active (non-paused) time
    val movingTimeMs: Long = 0,
    val steps: Int = 0,
    // step-counter state, persisted so the reboot guard survives process death
    val baselineSteps: Long = -1,
    val stepOffset: Int = 0,
    val lastHardwareSteps: Long = -1
)

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("activityId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val accuracy: Float,
    val timestamp: Long
)

@Dao interface ActivityDao {
    @Insert suspend fun insert(a: ActivityEntity): Long
    @Query("SELECT * FROM activities WHERE id = :id") suspend fun byId(id: Long): ActivityEntity?
    @Query("SELECT * FROM activities WHERE status != 'FINISHED' ORDER BY id DESC LIMIT 1")
    suspend fun unfinished(): ActivityEntity?
    @Query("SELECT * FROM activities WHERE status = 'FINISHED' ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<ActivityEntity>>
    @Query(
        """UPDATE activities SET distanceMeters = :distance, elapsedMs = :elapsed, movingTimeMs = :moving,
           steps = :steps, baselineSteps = :baseline, stepOffset = :offset, lastHardwareSteps = :last WHERE id = :id"""
    )
    suspend fun saveProgress(id: Long, distance: Double, elapsed: Long, moving: Long, steps: Int,
                             baseline: Long, offset: Int, last: Long)
    @Query("UPDATE activities SET status = :status, endedAt = :endedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, endedAt: Long?)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun delete(id: Long)
}

@Dao interface TrackPointDao {
    @Insert suspend fun insert(p: TrackPointEntity)
    @Query("SELECT * FROM track_points WHERE activityId = :id ORDER BY timestamp")
    suspend fun forActivity(id: Long): List<TrackPointEntity>
}
