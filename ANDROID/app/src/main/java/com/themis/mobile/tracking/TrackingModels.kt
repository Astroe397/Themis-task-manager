package com.themis.mobile.tracking

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TrackingStatus { IDLE, RECORDING, PAUSED }

data class GeoPoint(val lat: Double, val lon: Double)

/** Everything the UI shows. Built by the service, observed by the ViewModel through [TrackingStore]. */
data class TrackingState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val activityId: Long = 0,
    val distanceMeters: Double = 0.0,
    val elapsedMs: Long = 0,            // active time measured at [elapsedAnchor]
    val elapsedAnchor: Long = 0,        // SystemClock.elapsedRealtime() at that moment; the UI ticks from here
    val movingTimeMs: Long = 0,
    val steps: Int? = null,             // null = no step sensor or permission
    val currentPaceSecPerKm: Double? = null,
    val gpsAccuracy: Float? = null,
    val current: GeoPoint? = null,
    val route: List<GeoPoint> = emptyList(),   // Douglas-Peucker reduced: for drawing only
    val hasUnfinished: Boolean = false,        // a workout survived a process death and can be resumed
    val finishedActivityId: Long? = null       // set once when Done is tapped; the UI consumes it and shows the summary
) {
    fun elapsedNow(nowElapsedRealtime: Long): Long =
        if (status == TrackingStatus.RECORDING) elapsedMs + (nowElapsedRealtime - elapsedAnchor).coerceAtLeast(0) else elapsedMs
}

/** Process-wide hand-off between the foreground service (writer) and the UI (reader). */
@Singleton
class TrackingStore @Inject constructor() {
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()
    fun update(f: (TrackingState) -> TrackingState) = _state.update(f)
}
