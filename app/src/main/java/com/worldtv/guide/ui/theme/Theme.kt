package com.worldtv.guide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Classic feature-phone portal palette: near-black canvas, steel-blue bars, orange highlight. */
object Snap {
    val Canvas = Color(0xFF0A0C10)
    val Row = Color(0xFF14181F)
    val RowAlt = Color(0xFF10131A)
    val Line = Color(0xFF232A36)
    val BarTop = Color(0xFF3B4A66)
    val BarBottom = Color(0xFF151C2A)
    val KeyTop = Color(0xFF2A2F3A)
    val KeyBottom = Color(0xFF0D0F14)
    val Accent = Color(0xFFFF8C1A)
    val AccentDeep = Color(0xFFE05A00)
    val Text = Color(0xFFF2F4F8)
    val Muted = Color(0xFF9AA3B5)
    val Live = Color(0xFFFF4040)
    val Selected = Color(0xFF223A5E)

    val bar = Brush.verticalGradient(listOf(BarTop, BarBottom))
    val softKeys = Brush.verticalGradient(listOf(KeyTop, KeyBottom))
    val section = Brush.verticalGradient(listOf(Color(0xFF2B3446), Color(0xFF1A202C)))
    val accentBar = Brush.verticalGradient(listOf(Accent, AccentDeep))
}

val LiveRed = Snap.Live

private val scheme = darkColorScheme(
    primary = Snap.Accent, onPrimary = Color.Black,
    secondary = Color(0xFF6FC3FF), onSecondary = Color.Black,
    background = Snap.Canvas, onBackground = Snap.Text,
    surface = Snap.Row, onSurface = Snap.Text,
    surfaceVariant = Color(0xFF1E2430), onSurfaceVariant = Snap.Muted,
    surfaceContainer = Color(0xFF1A1F29),
    error = Color(0xFFFF6B6B),
)

private val base = Typography()
private fun TextStyle.sans() = copy(fontFamily = FontFamily.SansSerif)

private val typography = Typography(
    headlineSmall = base.headlineSmall.sans().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.sans().copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.sans().copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.sans(),
    bodyMedium = base.bodyMedium.sans(),
    bodySmall = base.bodySmall.sans(),
    labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.sans(),
    labelSmall = base.labelSmall.sans(),
)

@Composable
fun WorldTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
