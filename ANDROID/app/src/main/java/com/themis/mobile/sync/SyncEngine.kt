package com.themis.mobile.sync

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.themis.mobile.data.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

sealed interface SyncResult {
    data class Synced(val at: String) : SyncResult
    data object NotFound : SyncResult
    data class Failed(val message: String) : SyncResult
}

class SyncEngine(private val context: Context, private val db: ThemisDatabase) {
    private val prefs = context.getSharedPreferences("themis_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()   // foreground loop and WorkManager must never sync at the same time

    private fun api(h: DesktopHost, seconds: Long = 15): SyncApi =
        Retrofit.Builder()
            .baseUrl("http://${h.host}:${h.port}/")
            .client(OkHttpClient.Builder().callTimeout(seconds, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(SyncApi::class.java)

    private fun isEmulator() = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")

    /** Cached host first (fast path), then UDP discovery, then the emulator's host alias. */
    private suspend fun resolve(): DesktopHost? {
        prefs.getString("host", null)?.let { h ->
            val cached = DesktopHost(h, prefs.getInt("port", 54322))
            if (runCatching { api(cached, 2).status() }.isSuccess) return cached
        }
        val found = Discovery.find(context)
            ?: if (isEmulator()) DesktopHost("10.0.2.2", 54322) else null
        if (found != null) prefs.edit().putString("host", found.host).putInt("port", found.port).apply()
        return found
    }

    suspend fun sync(): SyncResult = mutex.withLock {
        try {
            val host = resolve() ?: return@withLock SyncResult.NotFound
            val api = api(host)
            val startedAt = nowIso()
            val since = prefs.getString("last_synced_at", null) ?: EPOCH

            // Collect local changes first so freshly pulled records aren't echoed straight back.
            val outgoing = collectLocal(since)
            val pulled = api.pull(PullRequest(since))
            if (!pulled.ok) return@withLock SyncResult.Failed("Pull rejected")
            applyRemote(pulled)
            val pushed = api.push(outgoing)
            if (!pushed.ok) return@withLock SyncResult.Failed("Push rejected")

            // Stamp with the time the sync STARTED, so edits made mid-sync are sent next time.
            prefs.edit().putString("last_synced_at", startedAt).apply()
            SyncResult.Synced(startedAt)
        } catch (e: Exception) {
            // Cached host may be stale (new IP): forget it so the next attempt rediscovers.
            prefs.edit().remove("host").apply()
            SyncResult.Failed(e.message ?: "Sync failed")
        }
    }

    private suspend fun collectLocal(since: String) = SyncPayload(
        tasks = db.taskDao().changedSince(since).map {
            TaskDto(it.uid, it.title, it.description, it.createdAt, it.updatedAt, if (it.isDeleted) 1 else 0,
                if (it.isOneOff) 1 else 0, if (it.isDone) 1 else 0)
        },
        completions = db.completionDao().changedSince(since).map {
            CompletionDto(it.taskUid, it.day, it.updatedAt, if (it.isDeleted) 1 else 0)
        },
        weights = db.weightDao().changedSince(since).map {
            WeightDto(it.day, it.kg, it.updatedAt, if (it.isDeleted) 1 else 0)
        },
        settings = db.settingDao().changedSince(since).map { SettingDto(it.key, it.value, it.updatedAt) }
    )

    /** Last-write-wins: a remote record only replaces a local one if its updatedAt is strictly newer. */
    private suspend fun applyRemote(r: PullResponse) = db.withTransaction {
        for (t in r.tasks.orEmpty()) {
            val local = db.taskDao().byUid(t.uid)
            val entity = TaskEntity(
                id = local?.id ?: 0, uid = t.uid, title = t.title, description = t.description ?: "",
                createdAt = t.createdAt, updatedAt = t.updatedAt, isDeleted = t.isDeleted != 0,
                isOneOff = t.isOneOff != 0, isDone = t.isDone != 0
            )
            if (local == null) db.taskDao().insert(entity)
            else if (t.updatedAt > local.updatedAt) db.taskDao().update(entity)
        }
        for (c in r.completions.orEmpty()) {
            val task = db.taskDao().byUid(c.taskUid) ?: continue   // tasks are applied first
            val local = db.completionDao().get(task.id, c.day)
            if (local == null || c.updatedAt > local.updatedAt)
                db.completionDao().upsert(CompletionEntity(task.id, c.day, c.updatedAt, c.isDeleted != 0))
        }
        for (w in r.weights.orEmpty()) {
            if (w.kg < 20 || w.kg > 500) continue
            val local = db.weightDao().get(w.day)
            if (local == null || w.updatedAt > local.updatedAt)
                db.weightDao().upsert(WeightEntity(w.day, w.kg, w.updatedAt, w.isDeleted != 0))
        }
        for (s in r.settings.orEmpty()) {
            val local = db.settingDao().get(s.key)
            if (local == null || s.updatedAt > local.updatedAt)
                db.settingDao().upsert(SettingEntity(s.key, s.value, s.updatedAt))
        }
    }
}
