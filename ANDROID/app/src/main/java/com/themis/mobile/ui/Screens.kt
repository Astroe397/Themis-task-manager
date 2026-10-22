package com.themis.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themis.mobile.data.trim1
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val fullDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
private val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")

/**
 * Top level: while a workout summary is open (after Done, or from the history) it takes over the screen,
 * with the share preview one step further in. Back always goes one step out.
 */
@Composable
fun ThemisRoot(vm: ThemisViewModel, trackVm: TrackingViewModel) {
    val summary by trackVm.summary.collectAsState()
    var sharing by rememberSaveable { mutableStateOf(false) }
    val s = summary
    if (s == null) {
        MainScaffold(vm, trackVm)
    } else {
        val close = { sharing = false; trackVm.closeSummary() }
        BackHandler { if (sharing) sharing = false else close() }
        if (sharing) SocialSharePreviewScreen(s) { sharing = false }
        else WorkoutSummaryScreen(s, onBack = close, onShare = { sharing = true })
    }
}

@Composable
private fun MainScaffold(vm: ThemisViewModel, trackVm: TrackingViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var taskMode by rememberSaveable { mutableIntStateOf(0) }   // 0 = daily, 1 = one-off
    val sync by vm.sync.collectAsState()
    val waterOn by vm.waterOn.collectAsState()
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "Notifications are blocked. Allow them in system settings to get water reminders.", Toast.LENGTH_LONG).show()
    }
    fun needsPermission() = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) { if (waterOn && needsPermission()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopBar(sync, waterOn, onToggleWater = {
                val on = !waterOn
                vm.setWater(on)
                if (on && needsPermission()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                Toast.makeText(ctx, if (on) "Water reminders on (every 30 min)" else "Water reminders off", Toast.LENGTH_SHORT).show()
            }, onSync = vm::syncNow)
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                listOf(
                    "Dashboard" to Icons.Default.Home, "Tasks" to Icons.Default.Check,
                    "Weight" to Icons.Default.Person, "Activity" to Icons.Default.LocationOn
                )
                    .forEachIndexed { i, (name, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, null) }, label = { Text(name) })
                    }
            }
        },
        floatingActionButton = {
            if (tab == 1) FloatingActionButton(onClick = { showAdd = true }, containerColor = Accent, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Default.Add, "Add task")
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DashboardScreen(vm) { day -> vm.selectDay(day); tab = 1 }
                1 -> TasksScreen(vm, taskMode) { taskMode = it }
                2 -> WeightScreen(vm)
                else -> ActivityScreen(trackVm)
            }
        }
    }
    if (showAdd) AddTaskDialog(vm, oneOff = taskMode == 1) { showAdd = false }
}

@Composable
private fun TopBar(sync: SyncUi, waterOn: Boolean, onToggleWater: () -> Unit, onSync: () -> Unit) {
    Column(Modifier.background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Themis", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleWater) {
                Icon(Icons.Default.Notifications,
                    if (waterOn) "Water reminders on. Tap to turn off" else "Water reminders off. Tap to turn on",
                    tint = if (waterOn) Accent else Muted.copy(alpha = 0.45f))
            }
            TextButton(onClick = onSync) {
                val c = when (sync.state) {
                    SyncState.Synced -> Color(0xFF2F8F5B); SyncState.Searching -> Color(0xFFD9A21B); SyncState.Offline -> Color(0xFFB0412F)
                }
                Box(Modifier.size(10.dp).background(c, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(sync.label, color = Muted, fontSize = 13.sp)
            }
            }
        }
        HorizontalDivider(thickness = 2.dp, color = Ink)
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Muted)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun DashboardScreen(vm: ThemisViewModel, onPickDay: (LocalDate) -> Unit) {
    val stats by vm.stats.collectAsState()
    val selected by vm.selectedDay.collectAsState()
    val quote by vm.quote.collectAsState()
    val ctx = LocalContext.current
    val s = stats ?: return
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            StatBlock("Current streak", "${s.streak} day${if (s.streak == 1) "" else "s"}", Modifier.weight(1f))
            StatBlock("Perfect days", s.perfect.toString(), Modifier.weight(1f))
            StatBlock("Days tracked", s.tracked.toString(), Modifier.weight(1f))
        }
        Panel {
            Text("Daily consistency", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Heatmap(s.days, s.today, selected, onPickDay)
            Legend()
        }
        Panel {
            Text("Average day", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Donut(s.avgDone, s.hasData)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBlock("done", if (s.hasData) "${trim1(s.avgDone)}%" else "–")
                    StatBlock("not done", if (s.hasData) "${trim1(100 - s.avgDone)}%" else "–")
                }
            }
            Text("Average of your daily completion across every day with at least one task.",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 12.dp))
        }
        quote?.let {   // hidden entirely when offline
            Column {
                Text("“${it.text}”", fontStyle = FontStyle.Italic, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 16.sp)
                Text(it.author, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        // Very bottom of the dashboard: light/dark switch.
        OutlinedButton(onClick = { ThemeMode.set(ctx, !ThemeMode.dark) }, Modifier.fillMaxWidth()) {
            Text(if (ThemeMode.dark) "Disable dark theme" else "Enable dark theme")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDlg(initial: LocalDate, max: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    fun ms(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = ms(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= ms(max)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state) }
}

@Composable
fun TasksScreen(vm: ThemisViewModel, mode: Int, onMode: (Int) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { onMode(0) }, label = { Text("Daily") })
            FilterChip(selected = mode == 1, onClick = { onMode(1) }, label = { Text("One-off") })
        }
        if (mode == 0) DailyTasks(vm) else OneOffTasks(vm)
    }
}

@Composable
private fun OneOffTasks(vm: ThemisViewModel) {
    val items by vm.oneOffs.collectAsState()
    val open = items.filter { !it.done }
    val done = items.filter { it.done }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<OneOffTask?>(null) }

    @Composable
    fun row(t: OneOffTask) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = t.done, onCheckedChange = { vm.setOneOffDone(t.id, it) },
                colors = CheckboxDefaults.colors(checkedColor = Accent))
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text(t.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = if (t.done) Muted else Ink,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null)
                if (t.description.isNotBlank()) Text(t.description, color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = { toDelete = t }) { Text("Delete", color = Muted, fontSize = 13.sp) }
        }
        HorizontalDivider(color = Line)
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Do it once, tick it, and it's done for good. These never touch your heatmap, streak or averages.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        HorizontalDivider(color = Line)
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            if (open.isEmpty()) item {
                Text("Nothing pending. Tap + to add a one-off task.", color = Muted, modifier = Modifier.padding(vertical = 18.dp))
            }
            items(open, key = { it.id }) { row(it) }
            if (done.isNotEmpty()) {
                item {
                    TextButton(onClick = { showDone = !showDone }) {
                        Text("Completed (${done.size})  ${if (showDone) "▲" else "▼"}", color = Muted)
                    }
                }
                if (showDone) items(done, key = { it.id }) { row(it) }
            }
        }
    }
    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete task?") },
            text = { Text("Delete “${t.title}”?") },
            confirmButton = { TextButton(onClick = { vm.deleteTask(t.id); toDelete = null }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DailyTasks(vm: ThemisViewModel) {
    val day by vm.selectedDay.collectAsState()
    val today by vm.today.collectAsState()
    val tasks by vm.dayTasks.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<DayTask?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val isToday = day == today

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (isToday) "Today" else day.format(weekday), style = MaterialTheme.typography.titleLarge)
                Text(day.format(fullDate), color = Muted, fontSize = 13.sp)
            }
            Row {
                if (!isToday) TextButton(onClick = { vm.selectDay(today) }) { Text("Back to today") }
                TextButton(onClick = { picking = true }) { Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(4.dp)); Text("Pick date") }
            }
        }
        error?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Line)
        if (tasks.isEmpty()) {
            Text("No tasks yet. Tap + to add your first one.", color = Muted, modifier = Modifier.padding(vertical = 18.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(tasks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(checked = t.done, onCheckedChange = { vm.setDone(t.id, day, it) { e -> error = e } },
                        colors = CheckboxDefaults.colors(checkedColor = Accent))
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        Text(t.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = if (t.done) Muted else Ink,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null)
                        if (t.description.isNotBlank()) Text(t.description, color = Muted, fontSize = 13.sp)
                    }
                    TextButton(onClick = { toDelete = t }) { Text("Delete", color = Muted, fontSize = 13.sp) }
                }
                HorizontalDivider(color = Line)
            }
        }
    }
    if (picking) DatePickerDlg(day, today, { vm.selectDay(it) }) { picking = false }
    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete task?") },
            text = { Text("Delete “${t.title}” and its history?") },
            confirmButton = { TextButton(onClick = { vm.deleteTask(t.id); toDelete = null }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AddTaskDialog(vm: ThemisViewModel, oneOff: Boolean, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (oneOff) "Add a one-off task" else "Add a task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { if (it.length <= 120) title = it }, label = { Text("Title") },
                    placeholder = { Text(if (oneOff) "e.g. Renew passport" else "e.g. Read for 20 minutes") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") },
                    placeholder = { Text(if (oneOff) "Any details (optional)" else "What counts as done?") }, minLines = 3)
                error?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
                Text(if (oneOff) "Done once, done for good. Doesn't affect your heatmap." else "New tasks count from today onward.",
                    color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.addTask(title, desc, oneOff) { e -> if (e == null) onDismiss() else error = e } }) { Text("Add task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun WeightScreen(vm: ThemisViewModel) {
    val data by vm.weight.collectAsState()
    val today by vm.today.collectAsState()
    val w = data ?: return
    var date by remember { mutableStateOf(today) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val existing = w.points.firstOrNull { it.day == date }
    LaunchedEffect(date, w.unit, existing?.value) { text = existing?.let { trim1(it.value) } ?: "" }   // prefill like desktop

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Weight · past 6 months", style = MaterialTheme.typography.titleLarge)
        fun f(v: Double?) = v?.let { "${trim1(it)} ${w.unit}" } ?: "–"
        Row(Modifier.fillMaxWidth()) {
            StatBlock("Latest", f(w.latest?.value), Modifier.weight(1f))
            StatBlock("6-month change", w.change?.let { (if (it > 0) "+" else "") + "${trim1(it)} ${w.unit}" } ?: "–", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            StatBlock("Lowest", f(w.lowest), Modifier.weight(1f))
            StatBlock("Highest", f(w.highest), Modifier.weight(1f))
        }
        Panel { WeightChart(w) }
        Panel {
            OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(8.dp)); Text(date.format(fullDate))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), label = { Text("Weight") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                listOf("kg", "lb").forEach { u ->
                    FilterChip(selected = w.unit == u, onClick = { vm.setUnit(u) }, label = { Text(u) })
                }
            }
            error?.let { Text(it, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    error = null
                    vm.saveWeight(date, text.replace(',', '.').toDoubleOrNull(), w.unit) { error = it }
                }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Save weight") }
                if (existing != null) TextButton(onClick = { vm.removeWeight(date) }) { Text("Remove entry") }
            }
            Text("One entry per day. Saving again on the same date replaces it.", color = Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (picking) DatePickerDlg(date, today, { date = it }) { picking = false }
}
