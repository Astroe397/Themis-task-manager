package com.themis.mobile.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

data class DayCell(val date: LocalDate, val total: Int, val done: Int, val pct: Int?, val future: Boolean)
data class Stats(
    val days: List<DayCell>, val today: LocalDate, val avgDone: Double,
    val tracked: Int, val perfect: Int, val streak: Int, val hasData: Boolean
)

/** Port of the desktop `get_stats`: Sunday-first week columns ending this week. */
fun computeStats(created: List<LocalDate>, doneByDay: Map<LocalDate, Int>, today: LocalDate, weeks: Int = 26): Stats {
    fun totals(d: LocalDate) = created.count { !it.isAfter(d) }
    val sinceSunday = today.dayOfWeek.value % 7            // Mon=1..Sat=6, Sun=7 -> 0
    val start = today.minusDays((sinceSunday + 7 * (weeks - 1)).toLong())
    val days = (0 until 7 * weeks).map { i ->
        val d = start.plusDays(i.toLong())
        val future = d.isAfter(today)
        val total = if (future) 0 else totals(d)
        val done = if (future) 0 else minOf(doneByDay[d] ?: 0, total)
        DayCell(d, total, done, if (total > 0) Math.round(100.0 * done / total).toInt() else null, future)
    }

    val pcts = mutableListOf<Double>()
    var perfect = 0
    created.minOrNull()?.let { first ->
        var d = first
        while (!d.isAfter(today)) {
            val t = totals(d)
            if (t > 0) {
                val done = minOf(doneByDay[d] ?: 0, t)
                pcts += done.toDouble() / t
                if (done == t) perfect++
            }
            d = d.plusDays(1)
        }
    }
    val avg = if (pcts.isEmpty()) 0.0 else Math.round(pcts.average() * 1000.0) / 10.0

    // Streak of fully completed days; today doesn't break it while still in progress.
    var streak = 0
    if (created.isNotEmpty()) {
        var d = today
        val tt = totals(today)
        if (!(tt > 0 && (doneByDay[today] ?: 0) >= tt)) d = today.minusDays(1)
        while (true) {
            val t = totals(d)
            if (t > 0 && (doneByDay[d] ?: 0) >= t) { streak++; d = d.minusDays(1) } else break
        }
    }
    return Stats(days, today, avg, pcts.size, perfect, streak, pcts.isNotEmpty())
}

data class WeightPoint(val day: LocalDate, val value: Double)
data class WeightData(
    val unit: String, val start: LocalDate, val end: LocalDate, val points: List<WeightPoint>,
    val latest: WeightPoint?, val change: Double?, val lowest: Double?, val highest: Double?
)

fun round1(v: Double) = Math.round(v * 10.0) / 10.0
fun trim1(v: Double) = round1(v).toString().removeSuffix(".0")

fun buildWeightData(all: List<WeightEntity>, unit: String, today: LocalDate): WeightData {
    val start = today.minusMonths(6)
    fun out(kg: Double) = round1(if (unit == "lb") kg / KG_PER_LB else kg)
    val mapped = all.map { WeightPoint(LocalDate.parse(it.day), out(it.kg)) }.sortedBy { it.day }
    val pts = mapped.filter { !it.day.isBefore(start) && !it.day.isAfter(today) }
    return WeightData(
        unit, start, today, pts, mapped.lastOrNull(),
        if (pts.size > 1) round1(pts.last().value - pts.first().value) else null,
        pts.minOfOrNull { it.value }, pts.maxOfOrNull { it.value }
    )
}

/** Shared by the chart (y-axis bounds): pad 12%, expand tiny ranges, snap to whole numbers. */
fun axisBounds(points: List<WeightPoint>): Pair<Double, Double> {
    var lo = points.minOf { it.value }; var hi = points.maxOf { it.value }
    if (hi - lo < 2) { lo -= 1; hi += 1 }
    val pad = (hi - lo) * 0.12
    return floor(lo - pad) to ceil(hi + pad)
}
