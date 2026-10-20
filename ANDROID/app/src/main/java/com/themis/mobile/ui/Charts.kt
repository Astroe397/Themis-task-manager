package com.themis.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themis.mobile.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

private fun levelColor(d: DayCell): Color? = when {
    d.future -> null
    d.total == 0 -> NoneCell
    d.pct == 0 -> Missed
    d.pct!! < 34 -> L1
    d.pct < 67 -> L2
    d.pct < 100 -> L3
    else -> L4
}

/** 26-week, Sunday-first consistency grid (GitHub style). Tap a day to open it. */
@Composable
fun Heatmap(days: List<DayCell>, today: LocalDate, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    val weeks = days.size / 7
    val cell = 15.dp
    val gap = 4.dp
    val top = 20.dp
    val gridH = top + (cell + gap) * 7
    val label = TextStyle(fontSize = 10.sp, color = Muted)
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }   // start at the latest week

    Row {
        Canvas(Modifier.width(30.dp).height(gridH)) {
            listOf("Mon" to 1, "Wed" to 3, "Fri" to 5).forEach { (t, row) ->
                drawText(measurer, t, Offset(0f, top.toPx() + row * (cell + gap).toPx()), label)
            }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier.width((cell + gap) * weeks).height(gridH).pointerInput(days) {
                    detectTapGestures { o ->
                        val step = (cell + gap).toPx()
                        val col = (o.x / step).toInt()
                        val row = ((o.y - top.toPx()) / step).toInt()
                        if (row in 0..6) days.getOrNull(col * 7 + row)?.let { if (!it.future) onPick(it.date) }
                    }
                }
            ) {
                val step = (cell + gap).toPx()
                val c = cell.toPx()
                var lastMonth = -1
                for (w in 0 until weeks) {
                    val first = days[w * 7].date
                    if (first.monthValue != lastMonth) {
                        lastMonth = first.monthValue
                        drawText(measurer, first.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                            Offset(w * step, 0f), label)
                    }
                }
                days.forEachIndexed { i, d ->
                    val color = levelColor(d) ?: return@forEachIndexed
                    val pos = Offset((i / 7) * step, top.toPx() + (i % 7) * step)
                    val r = CornerRadius(3.dp.toPx())
                    if (d.total == 0) {
                        drawRoundRect(Line, pos, Size(c, c), r, Stroke(1.dp.toPx()))
                    } else drawRoundRect(color, pos, Size(c, c), r)
                    if (d.date == today) drawRoundRect(Ink, pos - Offset(1.dp.toPx(), 1.dp.toPx()),
                        Size(c + 2.dp.toPx(), c + 2.dp.toPx()), r, Stroke(2.dp.toPx()))
                    if (d.date == selected) drawRoundRect(Accent, pos - Offset(2.dp.toPx(), 2.dp.toPx()),
                        Size(c + 4.dp.toPx(), c + 4.dp.toPx()), r, Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun Legend() {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        @Composable fun sw(c: Color) = Canvas(Modifier.size(13.dp)) { drawRoundRect(c, cornerRadius = CornerRadius(3.dp.toPx())) }
        sw(Missed); Text("Missed", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.width(6.dp)); sw(L1); sw(L2); sw(L3); sw(L4); Text("All done", fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun Donut(avgDone: Double, hasData: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val text = if (hasData) "${Math.round(avgDone)}%" else "–"
    Canvas(modifier.size(120.dp)) {
        val stroke = size.minDimension * 0.14f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(NotDone, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(Accent, -90f, (360f * avgDone / 100f).toFloat(), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val m = measurer.measure(text, TextStyle(fontSize = 22.sp, color = Ink, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        drawText(m, topLeft = Offset((size.width - m.size.width) / 2, (size.height - m.size.height) / 2))
    }
}

private class Geom(val w: Float, val h: Float, val d: WeightData) {
    val l = 40f; val r = 12f; val t = 12f; val b = 26f
    val t0 = d.start.toEpochDay(); val t1 = d.end.toEpochDay()
    val lo: Double; val hi: Double
    init { val (a, z) = axisBounds(d.points); lo = a; hi = z }
    fun x(day: LocalDate) = l + ((day.toEpochDay() - t0).toFloat() / maxOf(t1 - t0, 1L)) * (w - l - r)
    fun y(v: Double) = t + (1 - ((v - lo) / (hi - lo)).toFloat()) * (h - t - b)
}

@Composable
fun WeightChart(data: WeightData) {
    val measurer = rememberTextMeasurer()
    var sel by remember(data) { mutableStateOf<Int?>(null) }
    val label = TextStyle(fontSize = 10.sp, color = Muted)
    Column {
        if (data.points.isEmpty()) {
            Text("No weight entries in the past 6 months yet.", color = Muted, modifier = Modifier.padding(vertical = 48.dp))
            return@Column
        }
        Canvas(
            Modifier.fillMaxWidth().height(220.dp).pointerInput(data) {
                detectTapGestures { o ->
                    val g = Geom(size.width.toFloat(), size.height.toFloat(), data)
                    sel = data.points.indices.minByOrNull { abs(g.x(data.points[it].day) - o.x) }
                }
            }
        ) {
            val g = Geom(size.width, size.height, data)
            for (i in 0..4) {
                val v = g.lo + (g.hi - g.lo) * i / 4
                val y = g.y(v)
                drawLine(Line, Offset(g.l, y), Offset(size.width - g.r, y), 1.dp.toPx())
                val m = measurer.measure(trim1(v), label)
                drawText(m, topLeft = Offset(g.l - 6.dp.toPx() - m.size.width, y - m.size.height / 2f))
            }
            var mo = data.start.withDayOfMonth(1).plusMonths(1)
            while (!mo.isAfter(data.end)) {
                val m = measurer.measure(mo.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), label)
                drawText(m, topLeft = Offset(g.x(mo) - m.size.width / 2f, size.height - m.size.height - 2.dp.toPx()))
                mo = mo.plusMonths(1)
            }
            val path = Path()
            data.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(g.x(p.day), g.y(p.value)) else path.lineTo(g.x(p.day), g.y(p.value))
            }
            drawPath(path, Accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val radius = if (data.points.size <= 60) 4.dp.toPx() else 2.5.dp.toPx()
            data.points.forEachIndexed { i, p ->
                val c = Offset(g.x(p.day), g.y(p.value))
                drawCircle(if (i == sel) Accent else Surface, radius, c)
                drawCircle(Accent, radius, c, style = Stroke(2.dp.toPx()))
            }
        }
        val s = sel?.let { data.points.getOrNull(it) }
        Text(
            s?.let { "${it.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}: ${trim1(it.value)} ${data.unit}" }
                ?: "Tap a point for its value.",
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)
        )
    }
}
