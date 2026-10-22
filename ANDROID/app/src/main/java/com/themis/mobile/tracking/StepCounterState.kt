package com.themis.mobile.tracking

/**
 * Turns the cumulative hardware TYPE_STEP_COUNTER value into steps for one workout, with the reboot guard:
 * the hardware counter restarts at 0 after a reboot, so a reading lower than the previous one means a reset.
 * Steps counted so far are banked in [offset] and a new [baseline] is taken, so the total never goes backwards.
 */
class StepCounterState(var baseline: Long = -1, var offset: Int = 0, var last: Long = -1) {
    private var rebasePending = false

    fun live(): Int = if (baseline < 0) offset else offset + (last - baseline).toInt().coerceAtLeast(0)

    /** Call when the sensor is re-attached after a pause: steps taken while paused must not be counted. */
    fun markGap() { rebasePending = true }

    fun onSensor(current: Long): Int {
        when {
            baseline < 0 -> baseline = current                                   // first reading of the session
            rebasePending || current < last -> {                                 // resume-after-pause, or reboot/rollover
                offset += (last - baseline).toInt().coerceAtLeast(0)
                baseline = current
            }
        }
        rebasePending = false
        last = current
        return live()
    }
}
