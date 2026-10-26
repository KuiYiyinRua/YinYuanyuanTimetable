package com.yinyuanyuan.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF191919)
val Paper = Color(0xFFFAFAF8)
val Muted = Color(0xFF696967)
val Rule = Color(0xFFE1E1DD)
val Wash = Color(0xFFF0F0ED)

@Composable fun TimetableTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, onPrimary = Paper, primaryContainer = Wash,
            onPrimaryContainer = Ink, background = Paper, onBackground = Ink, surface = Paper,
            onSurface = Ink, onSurfaceVariant = Muted, surfaceVariant = Wash, outline = Muted,
            outlineVariant = Rule, error = Ink, onError = Paper, errorContainer = Wash, onErrorContainer = Ink,
            secondary = Ink, onSecondary = Paper, secondaryContainer = Wash, onSecondaryContainer = Ink,
            tertiary = Ink, onTertiary = Paper, tertiaryContainer = Wash, onTertiaryContainer = Ink,
            surfaceTint = Ink, surfaceDim = Wash, surfaceBright = Paper, surfaceContainerLowest = Paper,
            surfaceContainerLow = Paper, surfaceContainer = Wash, surfaceContainerHigh = Wash,
            surfaceContainerHighest = Rule, inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = Paper),
        typography = Typography(
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        ), content = content
    )
}

enum class Glyph { Grid, Today, Import, Settings, Left, Right, Close, Check, Arrow }

@Composable fun Mark(glyph: Glyph, color: Color = Ink, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x*u, y*u), Offset(xx*u, yy*u), 1.65f*u, StrokeCap.Round)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 1.5f) = drawRoundRect(color, Offset(x*u,y*u), Size(w*u,h*u), CornerRadius(r*u), style = Stroke(1.65f*u))
        when(glyph) {
            Glyph.Grid -> { rect(4f,5f,16f,15f); line(4f,10f,20f,10f); line(9f,10f,9f,20f); line(15f,10f,15f,20f); line(4f,15f,20f,15f); line(8f,3f,8f,7f); line(16f,3f,16f,7f) }
            Glyph.Today -> { rect(4f,5f,16f,15f); line(4f,10f,20f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f); drawCircle(color,2f*u,Offset(12f*u,15f*u)) }
            Glyph.Import -> { line(12f,3f,12f,15f); line(8f,11f,12f,15f); line(16f,11f,12f,15f); line(5f,16f,5f,20f); line(5f,20f,19f,20f); line(19f,16f,19f,20f) }
            Glyph.Settings -> { line(4f,6f,20f,6f); line(4f,12f,20f,12f); line(4f,18f,20f,18f); drawCircle(Paper,2.8f*u,Offset(9f*u,6f*u)); drawCircle(color,2.6f*u,Offset(9f*u,6f*u),style=Stroke(1.6f*u)); drawCircle(Paper,2.8f*u,Offset(16f*u,12f*u)); drawCircle(color,2.6f*u,Offset(16f*u,12f*u),style=Stroke(1.6f*u)); drawCircle(Paper,2.8f*u,Offset(10f*u,18f*u)); drawCircle(color,2.6f*u,Offset(10f*u,18f*u),style=Stroke(1.6f*u)) }
            Glyph.Left -> { line(14f,6f,8f,12f); line(8f,12f,14f,18f) }
            Glyph.Right -> { line(10f,6f,16f,12f); line(16f,12f,10f,18f) }
            Glyph.Close -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            Glyph.Check -> { line(5f,12f,10f,17f); line(10f,17f,20f,6f) }
            Glyph.Arrow -> { line(4f,12f,20f,12f); line(14f,6f,20f,12f); line(14f,18f,20f,12f) }
        }
    }
}
