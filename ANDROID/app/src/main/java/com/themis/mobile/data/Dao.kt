package com.themis.mobile.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao interface TaskDao {
    @Insert suspend fun insert(t: TaskEntity): Long
    @Update suspend fun update(t: TaskEntity)
    @Query("SELECT * FROM tasks WHERE uid = :uid") suspend fun byUid(uid: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun byId(id: Long): TaskEntity?
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY id") fun observeActive(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE updatedAt >= :since") suspend fun changedSince(since: String): List<TaskEntity>
}

@Dao interface CompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: CompletionEntity)
    @Query("SELECT * FROM completions WHERE taskId = :taskId AND day = :day")
    suspend fun get(taskId: Long, day: String): CompletionEntity?
    @Query("SELECT * FROM completions WHERE isDeleted = 0") fun observeActive(): Flow<List<CompletionEntity>>
    @Query(
        """SELECT t.uid AS taskUid, c.day AS day, c.updatedAt AS updatedAt, c.isDeleted AS isDeleted
           FROM completions c JOIN tasks t ON t.id = c.taskId WHERE c.updatedAt >= :since"""
    )
    suspend fun changedSince(since: String): List<CompletionSyncRow>
}

@Dao interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(w: WeightEntity)
    @Query("SELECT * FROM weights WHERE day = :day") suspend fun get(day: String): WeightEntity?
    @Query("SELECT * FROM weights WHERE isDeleted = 0 ORDER BY day") fun observeActive(): Flow<List<WeightEntity>>
    @Query("SELECT * FROM weights WHERE updatedAt >= :since") suspend fun changedSince(since: String): List<WeightEntity>
}

@Dao interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: SettingEntity)
    @Query("SELECT * FROM settings WHERE `key` = :key") suspend fun get(key: String): SettingEntity?
    @Query("SELECT * FROM settings WHERE `key` = :key") fun observe(key: String): Flow<SettingEntity?>
    @Query("SELECT * FROM settings WHERE updatedAt >= :since") suspend fun changedSince(since: String): List<SettingEntity>
}

@Database(
    entities = [TaskEntity::class, CompletionEntity::class, WeightEntity::class, SettingEntity::class],
    version = 2, exportSchema = false
)
abstract class ThemisDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun completionDao(): CompletionDao
    abstract fun weightDao(): WeightDao
    abstract fun settingDao(): SettingDao
}

/** v1 -> v2: one-off tasks. Existing rows become ordinary daily tasks. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN isOneOff INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN isDone INTEGER NOT NULL DEFAULT 0")
    }
}
