package com.themis.mobile.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private enum class Step { Foreground, Notifications, Disclosure, Background, Battery, Done }

/**
 * The sequential permission flow Android 10+/14+ requires. While [active], it walks through:
 *  1. location (fine + coarse) and activity recognition, requested together;
 *  2. notifications (Android 13+);
 *  3. a prominent in-app disclosure about background tracking;
 *  4. ACCESS_BACKGROUND_LOCATION, on its own, only after the user agrees;
 *  5. the battery-optimisation exemption (asked once).
 * Only missing location permission cancels it: the others make tracking better but aren't required.
 */
@Composable
fun TrackingPermissionFlow(active: Boolean, onFinished: () -> Unit, onCancelled: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("themis_prefs", Context.MODE_PRIVATE) }
    var step by remember { mutableStateOf(Step.Foreground) }

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun hasLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun needsBackground() = Build.VERSION.SDK_INT >= 29 &&
        !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) && !prefs.getBoolean("bg_declined", false)

    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocation()) step = Step.Notifications else { step = Step.Foreground; onCancelled() }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = Step.Disclosure }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = Step.Battery }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { step = Step.Done }

    LaunchedEffect(active, step) {
        if (!active) { step = Step.Foreground; return@LaunchedEffect }
        when (step) {
            Step.Foreground -> {
                val needed = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                if (needed.all { granted(it) }) step = Step.Notifications else foreground.launch(needed.toTypedArray())
            }
            Step.Notifications ->
                if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else step = Step.Disclosure
            Step.Disclosure -> if (!needsBackground()) step = Step.Battery   // otherwise the dialog below asks
            Step.Background -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            Step.Battery -> {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(ctx.packageName) || prefs.getBoolean("battery_asked", false)) step = Step.Done
                else {
                    prefs.edit().putBoolean("battery_asked", true).apply()
                    battery.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }
            }
            Step.Done -> onFinished()
        }
    }

    if (active && step == Step.Disclosure && needsBackground()) {
        val skip = { prefs.edit().putBoolean("bg_declined", true).apply(); step = Step.Battery }
        AlertDialog(
            onDismissRequest = skip,
            title = { Text("Track your route in the background") },
            text = {
                Text(
                    "Themis records your location to draw your route and measure distance and pace, including when " +
                        "the screen is off or the app is closed during an activity. It is only collected while an " +
                        "activity is recording, is stored on this phone, and is never uploaded.\n\n" +
                        "On the next screen, choose “Allow all the time” so tracking isn't cut off. You can still record " +
                        "without it, but Android may pause tracking when the screen is off."
                )
            },
            confirmButton = { TextButton(onClick = { step = Step.Background }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = skip) { Text("Not now") } }
        )
    }
}
