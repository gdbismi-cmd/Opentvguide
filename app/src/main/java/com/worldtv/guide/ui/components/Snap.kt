package com.worldtv.guide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldtv.guide.ui.theme.Snap
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Gradient title bar with an optional leading badge (flag/emoji), a live clock and action icons. */
@Composable
fun SnapTopBar(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); clock = LocalTime.now() } }
    val fmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Column(Modifier.fillMaxWidth().background(Snap.bar).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).shadow(2.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                        .background(Snap.accentBar),
                    contentAlignment = Alignment.Center,
                ) { Text(badge ?: "📺", fontSize = 17.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(title, color = Snap.BarText, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, color = Snap.BarMuted, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                if (onTitleClick != null) Text("  ▾", color = Snap.Accent, fontSize = 13.sp)
            }
            actions()
            Text(fmt.format(clock), color = Snap.BarText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Snap.accentBar))
    }
}

/** Feature-phone style soft-key bar: left key, centre key, right key. */
@Composable
fun SoftKeyBar(
    left: String?, onLeft: () -> Unit,
    right: String?, onRight: () -> Unit,
    center: ImageVector? = null, onCenter: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(Snap.softKeys).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            SoftKey(left, Modifier.weight(1f), TextAlign.Start, onLeft)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (center != null) Box(
                    Modifier.size(width = 56.dp, height = 34.dp).clip(RoundedCornerShape(17.dp))
                        .background(Snap.accentBar).clickable(onClick = onCenter),
                    contentAlignment = Alignment.Center,
                ) { Icon(center, null, tint = Snap.OnAccent, modifier = Modifier.size(20.dp)) }
            }
            SoftKey(right, Modifier.weight(1f), TextAlign.End, onRight)
        }
    }
}

@Composable
private fun SoftKey(label: String?, modifier: Modifier, align: TextAlign, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().then(if (label != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp),
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (label != null) Text(label, color = Snap.KeyText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Glossy rounded-square launcher tile with a label underneath. */
@Composable
fun SnapTile(label: String, icon: ImageVector, color: Color, badge: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth(0.78f).aspectRatio(1f)
                .shadow(6.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(color.lighten(0.25f), color, color.darken(0.35f))))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Gloss: a soft white highlight across the top half.
            Box(
                Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(15.dp))
                    .background(Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.35f), 0.48f to Color.White.copy(alpha = 0.08f),
                        0.5f to Color.Transparent, 1f to Color.Transparent,
                    )),
            )
            Icon(icon, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.48f))
            if (badge != null) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(9.dp))
                    .background(Snap.Live).padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Snap.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Gradient section divider used between list groups. */
@Composable
fun SnapSection(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Snap.section).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).background(Snap.Accent))
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), color = Snap.SectionText, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, color = Snap.SectionText.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

/** Plain menu row: leading slot, title, subtitle, chevron. Alternating background like old portal lists. */
@Composable
fun SnapMenuRow(
    title: String, subtitle: String? = null, index: Int = 0, selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Snap.Selected else if (index % 2 == 0) Snap.Row else Snap.RowAlt)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Snap.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Snap.Muted, fontSize = 12.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Snap.Accent)
    }
}

/** Small glossy square used as a list icon. */
@Composable
fun SnapIcon(icon: ImageVector, color: Color, size: Int = 34) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(color.lighten(0.25f), color.darken(0.3f)))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.6f).dp)) }
}

fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

