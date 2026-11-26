package com.intercom.ride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object IcColors {
    val Bg = Color(0xFF0B0D12)
    val Surface = Color(0xFF151922)
    val SurfaceHi = Color(0xFF1E2430)
    val Accent = Color(0xFFFF6A00)
    val AccentDim = Color(0x33FF6A00)
    val Good = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFC145)
    val Bad = Color(0xFFFF4D4F)
    val Text = Color(0xFFF2F4F8)
    val Muted = Color(0xFF8A93A6)
}

private val scheme = darkColorScheme(
    primary = IcColors.Accent,
    onPrimary = Color.Black,
    secondary = IcColors.Good,
    background = IcColors.Bg,
    onBackground = IcColors.Text,
    surface = IcColors.Surface,
    onSurface = IcColors.Text,
    surfaceVariant = IcColors.SurfaceHi,
    onSurfaceVariant = IcColors.Muted,
    error = IcColors.Bad,
)

private val type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

@Composable
fun IntercomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

/** Ten-segment audio meter. */
@Composable
fun LevelMeter(level: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(level, tween(80), label = "level")
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(10) { i ->
            val on = animated * 10 > i
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (on) color else IcColors.SurfaceHi),
            )
        }
    }
}

@Composable
fun SignalBars(bars: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight(i / 4f)
                    .align(androidx.compose.ui.Alignment.Bottom)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= bars) color else IcColors.SurfaceHi),
            )
        }
    }
}
