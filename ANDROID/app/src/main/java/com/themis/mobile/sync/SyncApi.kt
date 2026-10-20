package com.themis.mobile.sync

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// is_deleted travels as 0/1 (the desktop's SQLite ints), so it is an Int here, not a Boolean.
data class TaskDto(
    val uid: String, val title: String, val description: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_deleted") val isDeleted: Int,
    @SerializedName("is_one_off") val isOneOff: Int,
    @SerializedName("is_done") val isDone: Int
)
data class CompletionDto(
    @SerializedName("task_uid") val taskUid: String, val day: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_deleted") val isDeleted: Int
)
data class WeightDto(
    val day: String, val kg: Double,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_deleted") val isDeleted: Int
)
data class SettingDto(val key: String, val value: String, @SerializedName("updated_at") val updatedAt: String)

data class SyncPayload(
    val tasks: List<TaskDto>, val completions: List<CompletionDto>,
    val weights: List<WeightDto>, val settings: List<SettingDto>
)
data class PullRequest(val since: String)
data class PullResponse(
    val ok: Boolean, @SerializedName("server_time") val serverTime: String?,
    val tasks: List<TaskDto>?, val completions: List<CompletionDto>?,
    val weights: List<WeightDto>?, val settings: List<SettingDto>?
)
data class PushResponse(val ok: Boolean, @SerializedName("synced_at") val syncedAt: String?)
data class StatusDto(
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("last_synced_at") val lastSyncedAt: String?
)

interface SyncApi {
    @GET("api/v1/sync/status") suspend fun status(): StatusDto
    @POST("api/v1/sync/pull") suspend fun pull(@Body body: PullRequest): PullResponse
    @POST("api/v1/sync/push") suspend fun push(@Body body: SyncPayload): PushResponse
}
