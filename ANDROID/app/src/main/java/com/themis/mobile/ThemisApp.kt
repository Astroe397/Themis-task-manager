package com.themis.mobile

import android.app.Application
import androidx.work.WorkManager
import com.themis.mobile.data.Repository
import com.themis.mobile.data.ThemisDatabase
import com.themis.mobile.reminder.WaterReminder
import com.themis.mobile.sync.SyncEngine
import com.themis.mobile.sync.SyncWorker
import com.themis.mobile.ui.ThemeMode
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class ThemisApp : Application() {
    @Inject lateinit var db: ThemisDatabase
    @Inject lateinit var repo: Repository
    @Inject lateinit var syncEngine: SyncEngine

    override fun onCreate() {
        super.onCreate()   // Hilt injects the fields above here
        MapLibre.getInstance(this)
        ThemeMode.load(this)   // restore the saved light/dark choice before the first screen draws
        SyncWorker.schedule(this)   // periodic background sync on unmetered (Wi-Fi) networks
        // Water reminders used to run on WorkManager; they now use exact alarms. Drop the old job.
        WorkManager.getInstance(this).cancelUniqueWork("water-reminder")
        if (WaterReminder.isEnabled(this)) WaterReminder.schedule(this)   // re-arm the chain on every launch
    }
}
