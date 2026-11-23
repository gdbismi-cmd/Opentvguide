package com.worldtv.guide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One portal-style skin. Bars (title, soft keys, sections) keep their own text colours. */
data class Palette(
    val id: String,
    val name: String,
    val light: Boolean,
    val canvas: Color, val row: Color, val rowAlt: Color, val line: Color,
    val well: Color,            // dark/light inset behind logos and posters
    val liveRow: Color,
    val selected: Color,
    val barTop: Color, val barBottom: Color, val barText: Color, val barMuted: Color,
    val keyTop: Color, val keyBottom: Color, val keyText: Color,
    val sectionTop: Color, val sectionBottom: Color, val sectionText: Color,
    val accent: Color, val accentDeep: Color, val onAccent: Color,
    val text: Color, val muted: Color,
    val ok: Color = Color(0xFF7CD992), val bad: Color = Color(0xFFFF6B6B),
    val live: Color = Color(0xFFFF4040),
)

object Palettes {
    /** Dark canvas, steel-blue bars, orange highlights. */
    val Classic = Palette(
        "classic", "Snaptu Classic", light = false,
        canvas = Color(0xFF0A0C10), row = Color(0xFF14181F), rowAlt = Color(0xFF10131A), line = Color(0xFF232A36),
        well = Color(0xFF0E1118), liveRow = Color(0xFF1A2333), selected = Color(0xFF223A5E),
        barTop = Color(0xFF3B4A66), barBottom = Color(0xFF151C2A), barText = Color(0xFFF2F4F8), barMuted = Color(0xFF9AA3B5),
        keyTop = Color(0xFF2A2F3A), keyBottom = Color(0xFF0D0F14), keyText = Color(0xFFF2F4F8),
        sectionTop = Color(0xFF2B3446), sectionBottom = Color(0xFF1A202C), sectionText = Color(0xFFF2F4F8),
        accent = Color(0xFFFF8C1A), accentDeep = Color(0xFFE05A00), onAccent = Color.Black,
        text = Color(0xFFF2F4F8), muted = Color(0xFF9AA3B5),
    )

    /** White list screens under a glossy blue title bar and a black soft-key bar. */
    val Light = Palette(
        "light", "Snaptu Light", light = true,
        canvas = Color(0xFFE9ECF1), row = Color(0xFFFFFFFF), rowAlt = Color(0xFFF2F4F7), line = Color(0xFFD3D8E0),
        well = Color(0xFFDDE2EA), liveRow = Color(0xFFFFF1DE), selected = Color(0xFFD6E6FF),
        barTop = Color(0xFF4F8EDC), barBottom = Color(0xFF1C4C93), barText = Color.White, barMuted = Color(0xFFD5E4FA),
        keyTop = Color(0xFF3A3F48), keyBottom = Color(0xFF0E1015), keyText = Color.White,
        sectionTop = Color(0xFFDFE5EE), sectionBottom = Color(0xFFC9D2DF), sectionText = Color(0xFF1C2433),
        accent = Color(0xFFF57C00), accentDeep = Color(0xFFD84B00), onAccent = Color.White,
        text = Color(0xFF151A23), muted = Color(0xFF5E6878),
        ok = Color(0xFF1E8E3E), bad = Color(0xFFD32F2F), live = Color(0xFFE53935),
    )

    /** Deep navy with cyan highlights. */
    val Ocean = Palette(
        "ocean", "Snaptu Ocean", light = false,
        canvas = Color(0xFF06101C), row = Color(0xFF0C1A2B), rowAlt = Color(0xFF091524), line = Color(0xFF16304A),
        well = Color(0xFF051019), liveRow = Color(0xFF0F2A40), selected = Color(0xFF12406A),
        barTop = Color(0xFF1F6FB2), barBottom = Color(0xFF0A2846), barText = Color.White, barMuted = Color(0xFFA9C9E6),
        keyTop = Color(0xFF173452), keyBottom = Color(0xFF050D17), keyText = Color.White,
        sectionTop = Color(0xFF173A5C), sectionBottom = Color(0xFF0E2539), sectionText = Color.White,
        accent = Color(0xFF29C5F6), accentDeep = Color(0xFF0288D1), onAccent = Color(0xFF00121F),
        text = Color(0xFFEAF4FF), muted = Color(0xFF8FAAC4),
    )

    /** Graphite with green highlights. */
    val Lime = Palette(
        "lime", "Snaptu Lime", light = false,
        canvas = Color(0xFF0B0D0B), row = Color(0xFF151915), rowAlt = Color(0xFF111411), line = Color(0xFF253025),
        well = Color(0xFF0D100D), liveRow = Color(0xFF1B2A1A), selected = Color(0xFF26402A),
        barTop = Color(0xFF4B5A4B), barBottom = Color(0xFF181E18), barText = Color.White, barMuted = Color(0xFFB2C2B2),
        keyTop = Color(0xFF2B312B), keyBottom = Color(0xFF0B0D0B), keyText = Color.White,
        sectionTop = Color(0xFF2C362C), sectionBottom = Color(0xFF1A201A), sectionText = Color.White,
        accent = Color(0xFF8BD62B), accentDeep = Color(0xFF4E9A06), onAccent = Color(0xFF0E1A00),
        text = Color(0xFFF0F5EE), muted = Color(0xFF9BAA98),
    )

    /** Dark plum with pink highlights. */
    val Berry = Palette(
        "berry", "Snaptu Berry", light = false,
        canvas = Color(0xFF110A10), row = Color(0xFF1C121B), rowAlt = Color(0xFF170F16), line = Color(0xFF34223A),
        well = Color(0xFF120B12), liveRow = Color(0xFF2C1530), selected = Color(0xFF4A1F4E),
        barTop = Color(0xFF7A2D7E), barBottom = Color(0xFF2A0F2D), barText = Color.White, barMuted = Color(0xFFE0B8E2),
        keyTop = Color(0xFF34203A), keyBottom = Color(0xFF0F0910), keyText = Color.White,
        sectionTop = Color(0xFF3A2440), sectionBottom = Color(0xFF24152A), sectionText = Color.White,
        accent = Color(0xFFFF4FA3), accentDeep = Color(0xFFC2185B), onAccent = Color.White,
        text = Color(0xFFFBEFFA), muted = Color(0xFFB79CB5),
    )

    val all = listOf(Classic, Light, Ocean, Lime, Berry)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Classic
}

/**
 * Current skin. Every colour is read through snapshot state, so switching the
 * palette recomposes the whole app instantly.
 */
object Snap {
    var palette by mutableStateOf(Palettes.Classic)

    val Canvas get() = palette.canvas
    val Row get() = palette.row
    val RowAlt get() = palette.rowAlt
    val Line get() = palette.line
    val Well get() = palette.well
    val LiveRow get() = palette.liveRow
    val Selected get() = palette.selected
    val Accent get() = palette.accent
    val AccentDeep get() = palette.accentDeep
    val OnAccent get() = palette.onAccent
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Live get() = palette.live
    val Ok get() = palette.ok
    val Bad get() = palette.bad
    val BarText get() = palette.barText
    val BarMuted get() = palette.barMuted
    val KeyText get() = palette.keyText
    val SectionText get() = palette.sectionText

    val bar: Brush get() = Brush.verticalGradient(listOf(palette.barTop, palette.barBottom))
    val softKeys: Brush get() = Brush.verticalGradient(listOf(palette.keyTop, palette.keyBottom))
    val section: Brush get() = Brush.verticalGradient(listOf(palette.sectionTop, palette.sectionBottom))
    val accentBar: Brush get() = Brush.verticalGradient(listOf(palette.accent, palette.accentDeep))
}

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
    val p = Snap.palette
    val scheme = if (p.light) lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, secondary = p.barBottom, onSecondary = Color.White,
        background = p.canvas, onBackground = p.text, surface = p.row, onSurface = p.text,
        surfaceVariant = p.rowAlt, onSurfaceVariant = p.muted, surfaceContainer = p.row,
        surfaceContainerHigh = p.row, outline = p.line, error = p.bad,
    ) else darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, secondary = p.accent, onSecondary = p.onAccent,
        background = p.canvas, onBackground = p.text, surface = p.row, onSurface = p.text,
        surfaceVariant = p.rowAlt, onSurfaceVariant = p.muted, surfaceContainer = p.row,
        surfaceContainerHigh = p.selected, outline = p.line, error = p.bad,
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

val LiveRed: Color get() = Snap.Live
