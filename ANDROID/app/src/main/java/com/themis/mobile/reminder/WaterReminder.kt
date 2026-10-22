package com.themis.mobile.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.themis.mobile.MainActivity
import com.themis.mobile.R
import com.themis.mobile.sync.Quote
import com.themis.mobile.sync.QuoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Android-only: a "drink water" notification with a ZenQuotes quote every 30 minutes (on the :00 and :30).
 *
 * Runs on AlarmManager, not on a live process: each alarm wakes [WaterAlarmReceiver], which arms the next
 * alarm and posts the notification, so it keeps working while the app is closed. A reboot or app update
 * re-arms the chain through [BootReceiver]. (Force-stopping the app from system settings cancels all
 * its alarms; opening the app again re-arms them.)
 */
object WaterReminder {
    private const val CHANNEL_ID = "water_reminders"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "themis_prefs"
    private const val KEY_ENABLED = "water_enabled"
    const val ACTION_FIRE = "com.themis.mobile.WATER_REMINDER"

    // Quiet hours: nothing from 23:00 until 09:00 (the first reminder of the day is at 09:00).
    private const val QUIET_START_HOUR = 23
    private const val QUIET_END_HOUR = 9

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) schedule(ctx) else cancel(ctx)
    }

    fun inQuietHours(t: LocalTime): Boolean =
        if (QUIET_START_HOUR > QUIET_END_HOUR) t.hour >= QUIET_START_HOUR || t.hour < QUIET_END_HOUR
        else t.hour in QUIET_START_HOUR until QUIET_END_HOUR

    /** Next :00/:30 after [after] that is outside quiet hours (skips straight to 09:00 overnight). */
    fun nextTrigger(after: ZonedDateTime): ZonedDateTime {
        var next = after.withSecond(0).withNano(0)
        next = if (next.minute < 30) next.withMinute(30) else next.plusHours(1).withMinute(0)
        if (inQuietHours(next.toLocalTime())) {
            next = next.withHour(QUIET_END_HOUR).withMinute(0)
            if (!next.isAfter(after)) next = next.plusDays(1)   // e.g. 23:00 -> 09:00 *tomorrow*
        }
        return next
    }

    private fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, WaterAlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Arms (or re-arms) the next alarm. Safe to call repeatedly: it replaces the pending one. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTrigger(ZonedDateTime.now()).toInstant().toEpochMilli()
        val pi = pendingIntent(ctx)
        try {
            // Exact when the user/OS allows it, otherwise an inexact alarm that still fires in Doze.
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(ctx))
    }

    fun show(ctx: Context, quote: Quote?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A drink-water nudge and a quote every 30 minutes (not between 23:00 and 09:00)"
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
        try { nm.notify(NOTIFICATION_ID, n) }   // same id: replaces the previous reminder
        catch (_: SecurityException) { }
    }
}

class WaterAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WaterReminder.ACTION_FIRE) return
        val app = context.applicationContext
        if (!WaterReminder.isEnabled(app)) return
        WaterReminder.schedule(app)   // arm the NEXT alarm first, so the chain can never break
        if (WaterReminder.inQuietHours(LocalTime.now())) return   // delivered late into quiet hours

        val pending = goAsync()   // keep the process alive while the quote loads
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val quote = withTimeoutOrNull(7_000) { QuoteClient.fetch() }   // offline: still remind, no quote
                WaterReminder.show(app, quote)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (WaterReminder.isEnabled(context)) WaterReminder.schedule(context)
    }
}
