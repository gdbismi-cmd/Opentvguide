package com.worldtv.guide.ui.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldtv.guide.data.Programme
import com.worldtv.guide.ui.theme.Snap
import com.worldtv.guide.util.localTime
import java.time.Instant

/** Width of one minute on the timeline. 3.5 dp → 210 dp per hour. */
private val PerMin = 3.5.dp
private val LabelWidth = 96.dp
private val RowHeight = 62.dp
private val HeaderHeight = 30.dp

private class GridChannel(val name: String, val logo: String?, val programmes: List<Programme>)

private fun minutesBetween(a: Instant, b: Instant) = (b.epochSecond - a.epochSecond) / 60f

/**
 * Classic EPG grid: channels down the left, a time ruler across the top, one cell per
 * programme sized by its duration. The whole grid scrolls sideways together while the
 * channel column and ruler stay pinned.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideGrid(
    programmes: List<Programme>,
    windowStart: Instant,
    windowEnd: Instant,
    initialFocus: Instant,
    now: Instant,
    favKeys: Set<String>,
    hScroll: ScrollState,
    onProgramme: (Programme) -> Unit,
) {
    val channels = remember(programmes, windowStart, windowEnd) {
        programmes.asSequence()
            .filter { it.end.isAfter(windowStart) && it.start.isBefore(windowEnd) }
            .groupBy { it.channelKey }.values
            .map { list ->
                // Drop airings that overlap an earlier one on the same channel (duplicate feeds).
                val clean = ArrayList<Programme>()
                list.sortedBy { it.start }.forEach { p ->
                    val last = clean.lastOrNull()
                    if (last == null || !p.start.isBefore(last.end.minusSeconds(60))) clean += p
                }
                GridChannel(list.first().channelName, list.firstNotNullOfOrNull { it.channelLogo }, clean)
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }
    val totalMinutes = minutesBetween(windowStart, windowEnd)
    val timelineWidth = PerMin.times(totalMinutes)
    val density = LocalDensity.current

    // Open at the focus time (e.g. 30 min before now, or prime time on other days).
    LaunchedEffect(windowStart, initialFocus) {
        val minutes = minutesBetween(windowStart, initialFocus).coerceIn(0f, totalMinutes)
        val px = with(density) { PerMin.times(minutes).toPx() }.toInt()
        hScroll.scrollTo(px)
    }

    if (channels.isEmpty()) return

    val nowX = if (now.isAfter(windowStart) && now.isBefore(windowEnd)) PerMin.times(minutesBetween(windowStart, now)) else null

    Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
        LazyColumn(Modifier.width(LabelWidth + timelineWidth).fillMaxHeight()) {
            stickyHeader(key = "ruler") { TimeRuler(windowStart, totalMinutes, nowX, hScroll) }
            itemsIndexed(channels, key = { _, c -> c.name + c.programmes.first().id }) { i, c ->
                GridRow(c, i, windowStart, windowEnd, now, nowX, favKeys, hScroll, onProgramme)
            }
        }
    }
}

/** Keeps a child pinned to the left edge of the visible area while the grid scrolls. */
private fun Modifier.pinLeft(scroll: ScrollState) = offset { IntOffset(scroll.value, 0) }

@Composable
private fun TimeRuler(start: Instant, totalMinutes: Float, nowX: Dp?, scroll: ScrollState) {
    Box(Modifier.height(HeaderHeight).width(LabelWidth + PerMin.times(totalMinutes)).background(Snap.section)) {
        // Half-hour ticks.
        var m = 0
        while (m <= totalMinutes) {
            val x = LabelWidth + PerMin.times(m.toFloat())
            val onHour = start.plusSeconds(m * 60L).atZone(java.time.ZoneId.systemDefault()).minute == 0
            Box(Modifier.absoluteOffset(x = x).width(1.dp).fillMaxHeight()
                .background(Snap.SectionText.copy(alpha = if (onHour) 0.45f else 0.18f)))
            Text(
                start.plusSeconds(m * 60L).localTime(),
                Modifier.absoluteOffset(x = x + 4.dp).padding(top = 7.dp),
                color = Snap.SectionText.copy(alpha = if (onHour) 1f else 0.7f),
                fontSize = 11.sp, fontWeight = if (onHour) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
            )
            m += 30
        }
        if (nowX != null) NowMarker(LabelWidth + nowX, label = true)
        // Pinned corner over the channel column.
        Box(
            Modifier.pinLeft(scroll).width(LabelWidth).fillMaxHeight().background(Snap.section),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Channel", Modifier.padding(start = 10.dp), color = Snap.SectionText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NowMarker(x: Dp, label: Boolean = false) {
    Box(Modifier.absoluteOffset(x = x - 1.dp).width(2.dp).fillMaxHeight().background(Snap.Live))
    if (label) Box(
        Modifier.absoluteOffset(x = x - 16.dp).padding(top = 1.dp).clip(RoundedCornerShape(3.dp))
            .background(Snap.Live).padding(horizontal = 4.dp),
    ) { Text("NOW", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GridRow(
    c: GridChannel, index: Int, start: Instant, end: Instant, now: Instant, nowX: Dp?,
    favKeys: Set<String>, scroll: ScrollState, onProgramme: (Programme) -> Unit,
) {
    val rowBg = if (index % 2 == 0) Snap.Row else Snap.RowAlt
    Box(Modifier.height(RowHeight).width(LabelWidth + PerMin.times(minutesBetween(start, end))).background(rowBg)) {
        c.programmes.forEach { p ->
            val s = if (p.start.isBefore(start)) start else p.start
            val e = if (p.end.isAfter(end)) end else p.end
            val x = LabelWidth + PerMin.times(minutesBetween(start, s))
            val w = PerMin.times(minutesBetween(s, e))
            if (w > 2.dp) GridCell(p, x, w, now, p.favKey in favKeys) { onProgramme(p) }
        }
        if (nowX != null) NowMarker(LabelWidth + nowX)
        Box(Modifier.align(Alignment.BottomStart).width(LabelWidth + PerMin.times(minutesBetween(start, end))).height(1.dp)
            .background(Snap.Line))
        ChannelLabel(c, rowBg, Modifier.pinLeft(scroll))
    }
}

@Composable
private fun ChannelLabel(c: GridChannel, bg: Color, modifier: Modifier) {
    Row(
        modifier.width(LabelWidth).fillMaxHeight().background(bg).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Snap.Well),
            contentAlignment = Alignment.Center,
        ) {
            Text(c.name.take(2).uppercase(), color = Snap.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (c.logo != null) AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(6.dp))
        Text(c.name, color = Snap.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
    }
    // Divider between the pinned column and the timeline.
    Box(modifier.absoluteOffset(x = LabelWidth - 1.dp).width(1.dp).fillMaxHeight().background(Snap.Line))
}

@Composable
private fun GridCell(p: Programme, x: Dp, w: Dp, now: Instant, fav: Boolean, onClick: () -> Unit) {
    val live = p.isOnNow(now)
    val ended = p.hasEnded(now)
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.absoluteOffset(x = x + 1.dp).width(w - 2.dp).fillMaxHeight().padding(vertical = 4.dp)
            .clip(shape)
            .background(if (live) Snap.LiveRow else Snap.Well.copy(alpha = if (ended) 0.45f else 0.9f))
            .border(if (live) 1.5.dp else 0.5.dp, if (live) Snap.Accent else Snap.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) {
                    Icon(Icons.Filled.Star, null, Modifier.size(11.dp), tint = Snap.Accent)
                    Spacer(Modifier.width(2.dp))
                }
                Text(p.title, color = if (ended) Snap.Muted else Snap.Text, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${p.start.localTime()}–${p.end.localTime()}" + (p.subtitle?.let { " · $it" } ?: ""),
                color = if (live) Snap.Accent else Snap.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
