package com.themis.mobile.tracking

import kotlin.math.max

/**
 * Real-time GPS smoothing for the incoming fix stream (latitude/longitude, variance-based).
 * `qMetresPerSecond` is how fast we believe the runner can change position: higher trusts new fixes more.
 * Use ONLY on the live stream. Douglas-Peucker is for post-processing/rendering and lives elsewhere.
 */
class KalmanLocationFilter(private val qMetresPerSecond: Float = 3f) {
    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1f      // < 0 means "no estimate yet"
    private var lastTimeMs = 0L

    fun reset() { variance = -1f }

    fun process(latitude: Double, longitude: Double, accuracyM: Float, timeMs: Long): GeoPoint {
        val acc = max(accuracyM, 1f)
        if (variance < 0) {
            lat = latitude; lon = longitude; variance = acc * acc; lastTimeMs = timeMs
        } else {
            val dt = timeMs - lastTimeMs
            if (dt > 0) {   // uncertainty grows with time since the last fix
                variance += dt * qMetresPerSecond * qMetresPerSecond / 1000f
                lastTimeMs = timeMs
            }
            val k = variance / (variance + acc * acc)   // Kalman gain
            lat += k * (latitude - lat)
            lon += k * (longitude - lon)
            variance = (1 - k) * variance
        }
        return GeoPoint(lat, lon)
    }
}
