package com.themis.mobile.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * App-wide light/dark switch, remembered across launches. The colour tokens below read it, so every
 * screen, chart and heatmap follows the switch without being changed individually.
 */
object ThemeMode {
    private const val PREFS = "themis_prefs"
    private const val KEY = "dark_theme"

    var dark by mutableStateOf(false)
        private set

    fun load(ctx: Context) { dark = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) }

    fun set(ctx: Context, on: Boolean) {
        dark = on
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}

private fun pick(light: Long, dark: Long) = Color(if (ThemeMode.dark) dark else light)

// Light values are the desktop style.css tokens. Dark values keep the same teal identity.
val Bg: Color get() = pick(0xFFE8EDF0, 0xFF12191D)
val Surface: Color get() = pick(0xFFF7F9FA, 0xFF1B2429)
val Ink: Color get() = pick(0xFF1C2830, 0xFFE6EDF0)
val Muted: Color get() = pick(0xFF5C6B75, 0xFF9AA8B0)
val Line: Color get() = pick(0xFFC6D0D6, 0xFF2E3A41)
val Accent: Color get() = pick(0xFF1F6F62, 0xFF4FB3A1)
val NoneCell: Color get() = pick(0xFFDBE3E7, 0xFF26323A)
val Missed: Color get() = pick(0xFFE6C9C2, 0xFF5A3A35)
val L1: Color get() = pick(0xFFB7D6CD, 0xFF1F4D45)     // heatmap: brighter = more done in dark mode
val L2: Color get() = pick(0xFF7FB8AA, 0xFF2B7A6C)
val L3: Color get() = pick(0xFF3F9181, 0xFF3FA593)
val L4: Color get() = pick(0xFF145A4F, 0xFF6FD6C2)
val NotDone: Color get() = pick(0xFFC3CCD2, 0xFF3A4750)
val ErrorRed: Color get() = pick(0xFF9B2C1F, 0xFFE5786A)

private val serif = FontFamily.Serif
private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    titleLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 17.sp),
)

@Composable
fun ThemisTheme(content: @Composable () -> Unit) {
    val dark = ThemeMode.dark
    val scheme = if (dark) darkColorScheme(
        primary = Accent, onPrimary = Color(0xFF07201B), background = Bg, onBackground = Ink,
        surface = Surface, onSurface = Ink, surfaceVariant = NoneCell, onSurfaceVariant = Muted,
        outline = Line, secondaryContainer = L1, onSecondaryContainer = Ink, error = ErrorRed
    ) else lightColorScheme(
        primary = Accent, onPrimary = Color.White, background = Bg, onBackground = Ink,
        surface = Surface, onSurface = Ink, surfaceVariant = NoneCell, onSurfaceVariant = Muted,
        outline = Line, secondaryContainer = L1, onSecondaryContainer = Ink, error = ErrorRed
    )

    // Window background (no flash behind the UI) and system bars follow the theme too.
    val view = LocalView.current
    val bg = Bg
    val surface = Surface
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg.toArgb()))
            @Suppress("DEPRECATION") w.statusBarColor = bg.toArgb()
            @Suppress("DEPRECATION") w.navigationBarColor = surface.toArgb()
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/** Flat card: surface fill with a 1 dp line border, like the desktop panels. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Surface).border(1.dp, Line).padding(16.dp), content = content)
}
