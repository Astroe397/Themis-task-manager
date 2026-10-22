package com.themis.mobile.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.*   // GraphicsLayer, drawLayer, toImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themis.mobile.R
import com.themis.mobile.share.ShareUtils
import com.themis.mobile.tracking.GeoPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Orange = Color(0xFFFC4C02)          // Strava-style route colour
private val whenFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private fun startedText(s: WorkoutSummary) =
    Instant.ofEpochMilli(s.activity.startedAt).atZone(ZoneId.systemDefault()).format(whenFmt)

// ---------------------------------------------------------------- summary screen
/** Shown after Done is tapped (the workout is already saved in Room) and when a past activity is opened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(s: WorkoutSummary, onBack: () -> Unit, onShare: () -> Unit) {
    val a = s.activity
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Workout summary") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share workout") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(startedText(s), color = Muted)
            RouteMap(route = s.route, current = null, follow = false, interactive = false,
                modifier = Modifier.fillMaxWidth().height(260.dp))
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Final metrics", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share workout", tint = Accent) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric("Distance", formatKm(a.distanceMeters), Modifier.weight(1f))
                    Metric("Time", formatDuration(a.elapsedMs), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric("Avg pace", formatPace(s.avgPaceSecPerKm), Modifier.weight(1f))
                    Metric("Steps", if (a.steps > 0) a.steps.toString() else "–", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onShare, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share workout")
                }
            }
            OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Muted)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------------------------------------------------------- share preview
/** Preview of the 9:16 card plus the two share actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialSharePreviewScreen(s: WorkoutSummary, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    /** Snapshot the card, encode it off the main thread, then hand the content:// URI to [action]. */
    fun run(action: (Context, Uri) -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                val uri = ShareUtils.saveToCache(ctx, bitmap)   // PNG encoding happens on Dispatchers.IO
                action(ctx, uri)
            } catch (e: Exception) {
                error = "Couldn't create the share image. Please try again."
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Share workout") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // The card is always laid out at exactly 1080x1920 px; here it is only scaled down to fit the screen.
                val scale = min(constraints.maxWidth / 1080f, constraints.maxHeight / 1920f)
                Box(
                    Modifier.align(Alignment.Center).layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(1080, 1920))
                        layout((1080 * scale).roundToInt(), (1920 * scale).roundToInt()) {
                            placeable.placeWithLayer(0, 0) {
                                scaleX = scale; scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    }
                ) { SocialShareCard(s, layer) }
            }
            error?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { run(ShareUtils::shareToInstagramStory) }, enabled = !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text(if (busy) "Preparing…" else "Share to Instagram Story", maxLines = 2) }
                OutlinedButton(onClick = { run(ShareUtils::shareGeneric) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Save / Share", maxLines = 2)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- the card itself
/**
 * Strava-style 9:16 card (1080 x 1920 px): dark background, glowing orange route, big metrics, branding.
 * A fixed density makes it pixel-identical on every phone, so the captured PNG is always Story resolution.
 * The route is drawn directly with Compose (no map tiles), which is what makes the snapshot reliable.
 */
@Composable
fun SocialShareCard(s: WorkoutSummary, layer: GraphicsLayer, modifier: Modifier = Modifier) {
    val a = s.activity
    CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 1f)) {   // 360 x 640 dp = 1080 x 1920 px
        Box(
            modifier
                .size(360.dp, 640.dp)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }   // capture everything below into the layer
                    drawLayer(layer)                                      // ...and still show it on screen
                }
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1418), Color(0xFF17292F))))
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp)) {
                Text("WORKOUT COMPLETE", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(startedText(s), color = Color(0xFF9FB0B8), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))

                RouteArt(s.route, Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.2f".format(a.distanceMeters / 1000.0), color = Color.White, fontSize = 76.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, lineHeight = 80.sp)
                    Text(" km", color = Color(0xFF9FB0B8), fontSize = 22.sp, modifier = Modifier.padding(bottom = 14.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    CardStat("TIME", formatDuration(a.elapsedMs), Modifier.weight(1f))
                    CardStat("AVG PACE", formatPace(s.avgPaceSecPerKm).removeSuffix(" /km"), Modifier.weight(1f), unit = "/km")
                    CardStat("STEPS", if (a.steps > 0) a.steps.toString() else "–", Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth().padding(top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.logo_emblem), null,
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White)
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("THEMIS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 3.sp)
                        Text("Liberating data. People's app.", color = Color(0xFF9FB0B8), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardStat(label: String, value: String, modifier: Modifier, unit: String? = null) {
    Column(modifier) {
        Text(label, color = Color(0xFF9FB0B8), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            if (unit != null) Text(unit, color = Color(0xFF9FB0B8), fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
    }
}

/** Fits the (already reduced) route into the box, keeping its true shape, with a soft neon glow. */
@Composable
private fun RouteArt(route: List<GeoPoint>, modifier: Modifier) {
    Canvas(modifier) {
        if (route.size < 2) return@Canvas
        val kx = cos(Math.toRadians(route.sumOf { it.lat } / route.size))   // shrink longitude so shapes aren't stretched
        val xs = route.map { it.lon * kx }
        val ys = route.map { it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = 24.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val spanX = max(maxX - minX, 1e-9)
        val spanY = max(maxY - minY, 1e-9)
        val scale = min(w / spanX, h / spanY)
        val offX = pad + (w - spanX * scale) / 2
        val offY = pad + (h - spanY * scale) / 2
        fun pt(i: Int) = Offset((offX + (xs[i] - minX) * scale).toFloat(), (offY + (maxY - ys[i]) * scale).toFloat())

        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until route.size) lineTo(pt(i).x, pt(i).y)
        }
        fun stroke(width: Float) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, Orange.copy(alpha = 0.14f), style = stroke(18.dp.toPx()))
        drawPath(path, Orange.copy(alpha = 0.32f), style = stroke(10.dp.toPx()))
        drawPath(path, Orange, style = stroke(4.5.dp.toPx()))
        drawCircle(Color(0xFF3DDC84), 7.dp.toPx(), pt(0))                                  // start
        drawCircle(Color.White, 7.dp.toPx(), pt(route.lastIndex))                          // finish
        drawCircle(Orange, 7.dp.toPx(), pt(route.lastIndex), style = Stroke(3.dp.toPx()))
    }
}
