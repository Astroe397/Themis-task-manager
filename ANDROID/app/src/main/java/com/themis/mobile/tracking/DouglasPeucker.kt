package com.themis.mobile.tracking

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Polyline reduction for DRAWING a route or exporting a finished one.
 * Never write its output back to the database: Room keeps every filtered fix.
 */
object DouglasPeucker {
    fun simplify(points: List<GeoPoint>, epsilonMeters: Double): List<GeoPoint> {
        val n = points.size
        if (n < 3) return points
        // Local flat projection in metres around the first point; accurate enough for a workout-sized area.
        val mx = 111_320.0 * cos(Math.toRadians(points[0].lat))
        val my = 110_540.0
        val x = DoubleArray(n) { (points[it].lon - points[0].lon) * mx }
        val y = DoubleArray(n) { (points[it].lat - points[0].lat) * my }

        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<IntArray>()      // iterative: a long run must not overflow the call stack
        stack.addLast(intArrayOf(0, n - 1))
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var worst = -1.0
            var idx = -1
            for (i in s + 1 until e) {
                val d = distToSegment(x[i], y[i], x[s], y[s], x[e], y[e])
                if (d > worst) { worst = d; idx = i }
            }
            if (idx != -1 && worst > epsilonMeters) {
                keep[idx] = true
                stack.addLast(intArrayOf(s, idx))
                stack.addLast(intArrayOf(idx, e))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun distToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
