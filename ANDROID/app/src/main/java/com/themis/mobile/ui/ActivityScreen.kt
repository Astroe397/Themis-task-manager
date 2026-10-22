package com.themis.mobile.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themis.mobile.data.ActivityEntity
import com.themis.mobile.tracking.TrackingStatus
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val whenFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
fun formatKm(m: Double) = "%.2f km".format(m / 1000.0)
fun formatPace(secPerKm: Double?): String {
    if (secPerKm == null || secPerKm.isNaN() || secPerKm > 60 * 60) return "–"
    val t = secPerKm.toInt()
    return "%d:%02d /km".format(t / 60, t % 60)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Muted)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

/** Record runs and walks: live map, distance, time, pace and steps, plus a history of finished workouts. */
@Composable
fun ActivityScreen(vm: TrackingViewModel) {
    val s by vm.state.collectAsState()
    val history by vm.history.collectAsState()
    var startFlow by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ActivityEntity?>(null) }
    var nowE by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // The service never wakes up just to redraw the clock; the screen ticks between GPS updates.
    LaunchedEffect(s.status) {
        while (s.status == TrackingStatus.RECORDING) { nowE = SystemClock.elapsedRealtime(); delay(1000) }
    }

    TrackingPermissionFlow(active = startFlow, onFinished = { startFlow = false; vm.start() }, onCancelled = { startFlow = false })

    val live = s.status != TrackingStatus.IDLE
    Column(Modifier.fillMaxSize()) {
        RouteMap(
            route = if (live) s.route else emptyList(),
            current = if (live) s.current else null,
            follow = s.status == TrackingStatus.RECORDING,
            modifier = Modifier.fillMaxWidth().height(250.dp)
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Metric("Distance", formatKm(s.distanceMeters), Modifier.weight(1f))
                        Metric("Time", formatDuration(s.elapsedNow(nowE)), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Metric("Pace", formatPace(s.currentPaceSecPerKm), Modifier.weight(1f))
                        Metric("Steps", s.steps?.toString() ?: "–", Modifier.weight(1f))
                    }
                    if (live) Text(
                        when {
                            s.status == TrackingStatus.PAUSED -> "Paused: GPS is off to save battery"
                            s.gpsAccuracy == null -> "Waiting for GPS…"
                            else -> "GPS accuracy ±${s.gpsAccuracy!!.toInt()} m"
                        }, color = Muted, fontSize = 12.sp
                    )
                }
            }
            item {
                when (s.status) {
                    TrackingStatus.IDLE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { startFlow = true }, Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Start activity") }
                        if (s.hasUnfinished) OutlinedButton(onClick = vm::restore, Modifier.fillMaxWidth()) {
                            Text("Resume unfinished workout")
                        }
                    }
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.status == TrackingStatus.RECORDING)
                            Button(onClick = vm::pause, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Pause") }
                        else
                            Button(onClick = vm::resume, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Resume") }
                        OutlinedButton(onClick = { confirmStop = true }, Modifier.weight(1f)) { Text("Stop", color = ErrorRed) }
                    }
                }
            }
            if (history.isNotEmpty()) item {
                Text("Past activities", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text("Tap one for its summary, route map and sharing.",
                    color = Muted, fontSize = 12.sp)
            }
            items(history, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openSummary(a.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Instant.ofEpochMilli(a.startedAt).atZone(ZoneId.systemDefault()).format(whenFmt),
                            color = Ink)
                        Text(
                            "${formatKm(a.distanceMeters)} · ${formatDuration(a.elapsedMs)}" +
                                (if (a.steps > 0) " · ${a.steps} steps" else ""),
                            color = Muted, fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = { toDelete = a }) { Text("Delete", color = Muted, fontSize = 13.sp) }
                }
                HorizontalDivider(color = Line)
            }
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Finish activity?") },
        text = { Text("Your route, distance and steps will be saved.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; vm.stop() }) { Text("Finish") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going") } }
    )
    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete activity?") },
            text = { Text("This removes the route and stats from this phone.") },
            confirmButton = { TextButton(onClick = { vm.delete(a.id); toDelete = null }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}
