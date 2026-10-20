package com.themis.mobile

import android.app.Application
import androidx.room.Room
import com.themis.mobile.data.MIGRATION_1_2
import com.themis.mobile.data.Repository
import com.themis.mobile.reminder.WaterReminder
import com.themis.mobile.data.ThemisDatabase
import com.themis.mobile.sync.SyncEngine
import com.themis.mobile.sync.SyncWorker

class ThemisApp : Application() {
    lateinit var db: ThemisDatabase
    lateinit var repo: Repository
    lateinit var syncEngine: SyncEngine

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, ThemisDatabase::class.java, "themis.db")
            .addMigrations(MIGRATION_1_2).build()
        repo = Repository(db)
        syncEngine = SyncEngine(this, db)
        SyncWorker.schedule(this)   // periodic background sync on unmetered (Wi-Fi) networks
        if (WaterReminder.isEnabled(this)) WaterReminder.schedule(this)
    }
}
