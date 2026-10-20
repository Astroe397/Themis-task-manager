package com.themis.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design tokens from the desktop style.css
val Bg = Color(0xFFE8EDF0)
val Surface = Color(0xFFF7F9FA)
val Ink = Color(0xFF1C2830)
val Muted = Color(0xFF5C6B75)
val Line = Color(0xFFC6D0D6)
val Accent = Color(0xFF1F6F62)
val NoneCell = Color(0xFFDBE3E7)
val Missed = Color(0xFFE6C9C2)
val L1 = Color(0xFFB7D6CD)
val L2 = Color(0xFF7FB8AA)
val L3 = Color(0xFF3F9181)
val L4 = Color(0xFF145A4F)
val NotDone = Color(0xFFC3CCD2)
val ErrorRed = Color(0xFF9B2C1F)

private val serif = FontFamily.Serif
private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    titleLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 17.sp),
)

@Composable
fun ThemisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent, onPrimary = Color.White, background = Bg, onBackground = Ink,
            surface = Surface, onSurface = Ink, surfaceVariant = NoneCell, onSurfaceVariant = Muted,
            outline = Line, secondaryContainer = L1, onSecondaryContainer = Ink, error = ErrorRed
        ),
        typography = typography, content = content
    )
}

/** Flat card: surface fill with a 1 dp line border, like the desktop panels. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Surface).border(1.dp, Line).padding(16.dp), content = content)
}
