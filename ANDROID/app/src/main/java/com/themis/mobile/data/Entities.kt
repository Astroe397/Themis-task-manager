package com.themis.mobile.data

import androidx.room.*
import java.util.UUID

/** `uid` is the cross-device identity (auto-increment ids would collide between phone and desktop). */
@Entity(tableName = "tasks", indices = [Index(value = ["uid"], unique = true)])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val createdAt: String,   // YYYY-MM-DD
    val updatedAt: String,   // UTC ISO timestamp
    val isDeleted: Boolean = false,
    /** One-off tasks are done once, forever. They never feed the heatmap, streak or averages. */
    @ColumnInfo(defaultValue = "0") val isOneOff: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDone: Boolean = false     // only meaningful when isOneOff
)

@Entity(tableName = "completions", primaryKeys = ["taskId", "day"])
data class CompletionEntity(
    val taskId: Long,
    val day: String,
    val updatedAt: String,
    val isDeleted: Boolean = false
)

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val day: String,
    val kg: Double,
    val updatedAt: String,
    val isDeleted: Boolean = false
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: String   // added so settings can merge by timestamp, as the desktop does
)

/** Completion joined with its task's uid, for sync. */
data class CompletionSyncRow(val taskUid: String, val day: String, val updatedAt: String, val isDeleted: Boolean)
