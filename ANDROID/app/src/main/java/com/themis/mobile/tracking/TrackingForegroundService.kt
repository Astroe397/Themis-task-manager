package com.themis.mobile.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.themis.mobile.MainActivity
import com.themis.mobile.R
import com.themis.mobile.data.TrackPointEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the UI calls to drive the service. Every command goes through startForegroundService. */
object TrackingController {
    fun start(ctx: Context) = send(ctx, TrackingForegroundService.ACTION_START)
    fun pause(ctx: Context) = send(ctx, TrackingForegroundService.ACTION_PAUSE)
    fun resume(ctx: Context) = send(ctx, TrackingForegroundService.ACTION_RESUME)
    fun stop(ctx: Context) = send(ctx, TrackingForegroundService.ACTION_STOP)
    fun restore(ctx: Context) = send(ctx, TrackingForegroundService.ACTION_RESTORE)

    private fun send(ctx: Context, action: String) = ContextCompat.startForegroundService(
        ctx, Intent(ctx, TrackingForegroundService::class.java).setAction(action)
    )
}

/**
 * Owns GPS + step recording so it keeps running with the screen off or the app in the background.
 * Declared foregroundServiceType="location|health", START_STICKY, and every fix is written to Room
 * immediately, so if the system kills the process the workout is restored from the database.
 * No location or sensor logic lives in a ViewModel or Composable.
 */
@AndroidEntryPoint
class TrackingForegroundService : LifecycleService(), SensorEventListener {
    @Inject lateinit var repo: ActivityRepository
    @Inject lateinit var store: TrackingStore

    private lateinit var fused: FusedLocationProviderClient
    private var sensorManager: SensorManager? = null
    private val mutex = Mutex()      // location, sensor and UI commands are applied strictly one at a time

    private val kalman = KalmanLocationFilter()
    private var steps = StepCounterState()
    private var lastPersistedSteps = 0

    private var status = TrackingStatus.IDLE
    private var activityId = 0L
    private var distance = 0.0
    private var elapsedBase = 0L           // active ms banked before the current run segment
    private var anchor = 0L                // elapsedRealtime when the current run segment began
    private var movingMs = 0L
    private val route = ArrayList<GeoPoint>()       // every accepted, filtered fix (source for rendering only)
    private var lastRaw: Location? = null
    private var lastCounted: GeoPoint? = null
    private var lastCountedTime = 0L
    private var segmentStart = true        // first fix after start/resume: no distance across the gap
    private var rejected = 0
    private val window = ArrayDeque<Pair<Long, Double>>()   // (time, cumulative metres) for current pace
    private var accuracy: Float? = null
    private var pace: Double? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lifecycleScope.launch { result.locations.forEach { onFix(it) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Activity tracking", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground()   // must happen promptly after startForegroundService, whatever the command is
        when (intent?.action) {
            ACTION_START -> lifecycleScope.launch { startNew() }
            ACTION_PAUSE -> lifecycleScope.launch { pause() }
            ACTION_RESUME -> lifecycleScope.launch { resume() }
            ACTION_STOP -> lifecycleScope.launch { stop() }
            else -> lifecycleScope.launch { restore() }   // ACTION_RESTORE, or null when the system restarts us
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- commands
    private suspend fun startNew() = mutex.withLock {
        if (status != TrackingStatus.IDLE) return@withLock
        resetSession()
        // An earlier workout that was never finished (process death, then the user chose "Start") is kept as finished.
        repo.unfinished()?.let { repo.setStatus(it.id, "FINISHED", System.currentTimeMillis()) }
        activityId = repo.create(System.currentTimeMillis())
        status = TrackingStatus.RECORDING
        anchor = SystemClock.elapsedRealtime()
        attachSensor()
        requestLocation()
        store.update { TrackingState() }
        publish(emptyList())
    }

    private suspend fun pause() = mutex.withLock {
        if (status != TrackingStatus.RECORDING) return@withLock
        elapsedBase = currentElapsed(SystemClock.elapsedRealtime())
        status = TrackingStatus.PAUSED
        detach()   // no GPS or sensor drain while paused
        persist()
        repo.setStatus(activityId, "PAUSED")
        publish()
    }

    private suspend fun resume() = mutex.withLock {
        if (status != TrackingStatus.PAUSED) return@withLock
        status = TrackingStatus.RECORDING
        anchor = SystemClock.elapsedRealtime()
        segmentStart = true; lastRaw = null; kalman.reset(); window.clear(); pace = null
        steps.markGap()   // steps taken while paused are not counted
        attachSensor()
        requestLocation()
        repo.setStatus(activityId, "RECORDING")
        publish()
    }

    private suspend fun stop() = mutex.withLock {
        if (status != TrackingStatus.IDLE) {
            elapsedBase = currentElapsed(SystemClock.elapsedRealtime())
            status = TrackingStatus.IDLE
            detach()
            var finishedId: Long? = null
            if (route.size < 2) repo.delete(activityId)   // nothing worth keeping, so no summary either
            else {
                persist()
                repo.setStatus(activityId, "FINISHED", System.currentTimeMillis())
                finishedId = activityId
            }
            store.update { TrackingState(finishedActivityId = finishedId) }   // the UI opens the workout summary
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Process death / START_STICKY restart: rebuild everything from Room and carry on. */
    private suspend fun restore() = mutex.withLock {
        if (status != TrackingStatus.IDLE) return@withLock
        val a = repo.unfinished()
        if (a == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return@withLock
        }
        resetSession()
        activityId = a.id
        distance = a.distanceMeters
        elapsedBase = a.elapsedMs
        movingMs = a.movingTimeMs
        steps = StepCounterState(a.baselineSteps, a.stepOffset, a.lastHardwareSteps)
        lastPersistedSteps = a.steps
        repo.loadPoints(a.id).forEach { route.add(GeoPoint(it.latitude, it.longitude)) }
        anchor = SystemClock.elapsedRealtime()
        if (a.status == "PAUSED") status = TrackingStatus.PAUSED
        else {
            status = TrackingStatus.RECORDING
            attachSensor()
            requestLocation()
        }
        publish(reduce())
    }

    // ---------------------------------------------------------------- live data
    private suspend fun onFix(loc: Location) = mutex.withLock {
        if (status != TrackingStatus.RECORDING) return@withLock
        accuracy = loc.accuracy
        if (loc.accuracy > MAX_ACCURACY_M) return@withLock                       // too fuzzy to trust
        if (loc.hasSpeed() && loc.speed > MAX_SPEED_MS) return@withLock          // faster than a human can run

        val prevRaw = lastRaw
        if (prevRaw != null && !segmentStart) {
            val dt = (loc.time - prevRaw.time) / 1000.0
            if (dt <= 0) return@withLock
            if (prevRaw.distanceTo(loc) / dt > MAX_SPEED_MS) {                   // GPS teleport
                if (++rejected < MAX_CONSECUTIVE_REJECTS) return@withLock
                segmentStart = true; kalman.reset()                              // anchor was the bad one: start over
            }
        }
        rejected = 0
        lastRaw = loc

        val here = kalman.process(loc.latitude, loc.longitude, loc.accuracy, loc.time)   // real-time Kalman only
        val prev = lastCounted
        var inc = 0.0
        var dtSec = 0.0
        if (prev != null && !segmentStart) {
            inc = distanceM(prev, here)
            if (inc < MIN_STEP_M) return@withLock                                // stationary jitter
            dtSec = (loc.time - lastCountedTime) / 1000.0
        }
        segmentStart = false
        if (inc > 0 && dtSec > 0) {
            distance += inc
            if (inc / dtSec >= MOVING_SPEED_MS) movingMs += (dtSec * 1000).toLong()
        }
        lastCounted = here
        lastCountedTime = loc.time

        window.addLast(loc.time to distance)
        while (window.size > 1 && loc.time - window.first().first > PACE_WINDOW_MS) window.removeFirst()
        pace = window.first().let { f ->
            val dMeters = distance - f.second
            val dSec = (loc.time - f.first) / 1000.0
            if (dMeters >= 5 && dSec > 0) dSec / (dMeters / 1000.0) else null
        }

        // Source of truth: the filtered fix goes straight to Room, un-reduced.
        repo.addPoint(
            TrackPointEntity(
                activityId = activityId, latitude = here.lat, longitude = here.lon,
                altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                speed = if (loc.hasSpeed()) loc.speed else if (dtSec > 0) (inc / dtSec).toFloat() else 0f,
                accuracy = loc.accuracy, timestamp = loc.time
            )
        )
        route.add(here)
        persist()
        publish(reduce())
    }

    override fun onSensorChanged(event: SensorEvent) {
        val hardware = event.values[0].toLong()
        lifecycleScope.launch {
            mutex.withLock {
                if (status != TrackingStatus.RECORDING) return@withLock
                val live = steps.onSensor(hardware)
                if (live - lastPersistedSteps >= 20) persist()
                store.update { it.copy(steps = live) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------------------------------------------------------------- plumbing
    private fun resetSession() {
        distance = 0.0; elapsedBase = 0; movingMs = 0
        route.clear(); window.clear()
        kalman.reset(); steps = StepCounterState(); lastPersistedSteps = 0
        lastRaw = null; lastCounted = null; lastCountedTime = 0
        segmentStart = true; rejected = 0; pace = null; accuracy = null
    }

    private fun currentElapsed(nowE: Long) =
        if (status == TrackingStatus.RECORDING) elapsedBase + (nowE - anchor) else elapsedBase

    private suspend fun persist() {
        val live = if (stepSensorUsable()) steps.live() else 0
        repo.saveProgress(activityId, distance, currentElapsed(SystemClock.elapsedRealtime()), movingMs, live, steps)
        lastPersistedSteps = live
    }

    /** Douglas-Peucker on a copy, for drawing only. The database keeps every point. */
    private suspend fun reduce(): List<GeoPoint> {
        val copy = route.toList()
        return withContext(Dispatchers.Default) { DouglasPeucker.simplify(copy, RENDER_EPSILON_M) }
    }

    private fun publish(reduced: List<GeoPoint>? = null) {
        val nowE = SystemClock.elapsedRealtime()
        store.update {
            it.copy(
                status = status, activityId = activityId, distanceMeters = distance,
                elapsedMs = currentElapsed(nowE), elapsedAnchor = nowE, movingTimeMs = movingMs,
                steps = if (stepSensorUsable()) steps.live() else null,
                currentPaceSecPerKm = pace, gpsAccuracy = accuracy,
                current = route.lastOrNull(), route = reduced ?: it.route, hasUnfinished = false
            )
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).let {
            try { it.notify(NOTIFICATION_ID, buildNotification()) } catch (_: SecurityException) { }
        }
    }

    private fun hasActivityRecognition() = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun stepSensorUsable() = hasActivityRecognition() && sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Hardware step counter: runs on the sensor hub, so the main processor stays asleep. Batched up to 10 s. */
    private fun attachSensor() {
        if (!hasActivityRecognition()) return
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 10_000_000)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .setMinUpdateDistanceMeters(3f)      // never poll at 1 s with no distance filter
            .build()
        try { fused.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
        catch (_: SecurityException) { lifecycleScope.launch { stop() } }
    }

    private fun detach() {
        fused.removeLocationUpdates(callback)
        sensorManager?.unregisterListener(this)
    }

    private fun goForeground() {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        // Android 14 only accepts the health type when a matching runtime permission is granted.
        if (Build.VERSION.SDK_INT >= 34 && hasActivityRecognition()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        try { ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type) }
        catch (_: Exception) { stopSelf() }   // not allowed to start, or location permission missing
    }

    private fun buildNotification(): Notification {
        val elapsed = currentElapsed(SystemClock.elapsedRealtime())
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun action(label: String, act: String, code: Int) = NotificationCompat.Action.Builder(
            0, label,
            PendingIntent.getForegroundService(
                this, code, Intent(this, TrackingForegroundService::class.java).setAction(act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()

        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_track)
            .setContentTitle(
                when (status) {
                    TrackingStatus.RECORDING -> "Recording activity"
                    TrackingStatus.PAUSED -> "Activity paused"
                    TrackingStatus.IDLE -> "Starting…"
                }
            )
            .setContentText("%.2f km".format(distance / 1000.0))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
        if (status == TrackingStatus.RECORDING) {
            // The chronometer ticks in the system UI, so the service never has to wake up to redraw the time.
            b.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - elapsed)
        } else b.setShowWhen(false)
        if (status == TrackingStatus.RECORDING) b.addAction(action("Pause", ACTION_PAUSE, 1))
        if (status == TrackingStatus.PAUSED) b.addAction(action("Resume", ACTION_RESUME, 2))
        if (status != TrackingStatus.IDLE) b.addAction(action("Stop", ACTION_STOP, 3))
        return b.build()
    }

    private fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val r = FloatArray(1)
        Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, r)
        return r[0].toDouble()
    }

    override fun onDestroy() {
        detach()
        // Killed mid-workout: Room still has it, so tell the UI it can be resumed.
        if (status != TrackingStatus.IDLE) store.update { TrackingState(hasUnfinished = true) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.themis.mobile.tracking.START"
        const val ACTION_PAUSE = "com.themis.mobile.tracking.PAUSE"
        const val ACTION_RESUME = "com.themis.mobile.tracking.RESUME"
        const val ACTION_STOP = "com.themis.mobile.tracking.STOP"
        const val ACTION_RESTORE = "com.themis.mobile.tracking.RESTORE"

        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 2001

        private const val MAX_ACCURACY_M = 15f          // reject fuzzy fixes
        private const val MAX_SPEED_MS = 12.0           // ~43 km/h: above that it's a GPS jump, not a runner
        private const val MIN_STEP_M = 1.0              // ignore sub-metre drift while standing
        private const val MOVING_SPEED_MS = 0.5
        private const val PACE_WINDOW_MS = 30_000L
        private const val RENDER_EPSILON_M = 3.0
        private const val MAX_CONSECUTIVE_REJECTS = 5
    }
}
