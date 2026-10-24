package com.themis.mobile.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.themis.mobile.MainActivity
import com.themis.mobile.R
import com.themis.mobile.sync.Quote
import com.themis.mobile.sync.QuoteClient
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Android-only feature: a "drink water" notification with a ZenQuotes quote every 30 minutes. */
object WaterReminder {
    private const val WORK_NAME = "water-reminder"
    private const val CHANNEL_ID = "water_reminders"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "themis_prefs"
    private const val KEY_ENABLED = "water_enabled"

    // Quiet hours, so you aren't buzzed at night. Set both to the same hour to get reminders 24/7.
    private const val QUIET_START_HOUR = 22   // from 22:00 ...
    private const val QUIET_END_HOUR = 7      // ... until 07:00

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) schedule(ctx) else WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<WaterReminderWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun inQuietHours(t: LocalTime): Boolean = when {
        QUIET_START_HOUR == QUIET_END_HOUR -> false
        QUIET_START_HOUR > QUIET_END_HOUR -> t.hour >= QUIET_START_HOUR || t.hour < QUIET_END_HOUR
        else -> t.hour in QUIET_START_HOUR until QUIET_END_HOUR
    }

    fun show(ctx: Context, quote: Quote?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A drink-water nudge and a quote every 30 minutes"
            }
        )
        val text = quote?.let { "“${it.text}” — ${it.author}" } ?: "Take a moment to hydrate."
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water)
            .setContentTitle("Time to drink water 💧")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n) }   // same id: replaces the previous one
        catch (_: SecurityException) { }
    }
}

class WaterReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!WaterReminder.isEnabled(applicationContext) || WaterReminder.inQuietHours(LocalTime.now())) return Result.success()
        // Quote is best-effort (6 s timeout): offline you still get the water reminder.
        WaterReminder.show(applicationContext, QuoteClient.fetch())
        return Result.success()
    }
}
