package com.themis.mobile.di

import android.content.Context
import androidx.room.Room
import com.themis.mobile.data.*
import com.themis.mobile.sync.SyncEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): ThemisDatabase =
        Room.databaseBuilder(c, ThemisDatabase::class.java, "themis.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    @Provides @Singleton fun repository(db: ThemisDatabase) = Repository(db)
    @Provides @Singleton fun syncEngine(@ApplicationContext c: Context, db: ThemisDatabase) = SyncEngine(c, db)
}
