package com.worldtv.guide.ui.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.worldtv.guide.data.FeedResult
import com.worldtv.guide.data.FeedStatus
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.Programme
import com.worldtv.guide.ui.components.CenterMessage
import com.worldtv.guide.ui.components.Loading
import com.worldtv.guide.ui.components.SearchField
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.theme.Snap
import com.worldtv.guide.util.localTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class GuideMode(val title: String) { ALL("Full Guide"), NOW("On Now"), TONIGHT("Tonight"), SEARCH("Search") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideScreen(
    vm: GuideViewModel, prefs: Prefs, mode: GuideMode,
    onProgramme: (Programme) -> Unit, onSources: () -> Unit, onCountry: () -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favShows by prefs.favShows.collectAsStateWithLifecycle()
    val favKeys = remember(favShows) { favShows.map { it.key }.toSet() }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Instant.now() } }

    // "On now" and "Tonight" always mean today.
    LaunchedEffect(mode) {
        if ((mode == GuideMode.NOW || mode == GuideMode.TONIGHT) && s.date != LocalDate.now()) vm.setDate(LocalDate.now())
    }
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val useGrid = s.grid && mode != GuideMode.SEARCH
    val zone = ZoneId.systemDefault()
    val dayStart = s.date.atStartOfDay(zone).toInstant()
    val (gridStart, gridEnd) = when (mode) {
        GuideMode.TONIGHT -> LocalDate.now().atTime(18, 0).atZone(zone).toInstant() to
            LocalDate.now().plusDays(1).atTime(2, 0).atZone(zone).toInstant()
        else -> dayStart to dayStart.plusSeconds(24 * 3600L)
    }
    // Computed once per day/view so the grid isn't pulled back while you scroll.
    val gridFocus = remember(mode, s.date, s.grid) {
        when {
            mode == GuideMode.TONIGHT -> gridStart
            isTodaySelected(s.date) -> Instant.now().minusSeconds(30 * 60)
            else -> s.date.atTime(18, 30).atZone(zone).toInstant()
        }
    }
    val gridProgrammes = remember(s.programmes, s.query, s.genre) {
        val q = s.query.trim()
        val genre: String? = s.genre
        s.programmes.filter { p ->
            (q.isEmpty() || p.title.contains(q, true) || p.channelName.contains(q, true)) &&
                (genre == null || genre in p.genres)
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(mode) { if (mode == GuideMode.SEARCH) runCatching { focus.requestFocus() } }

    val genres = remember(s.programmes) {
        s.programmes.flatMap { it.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(30).map { it.key }
    }
    val isToday = s.date == LocalDate.now()
    val filtered = remember(s.programmes, s.query, s.genre, s.onNowOnly, s.hideEnded, isToday, now, mode) {
        val q = s.query.trim()
        val genre: String? = s.genre
        val eveningStart = LocalDate.now().atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()
        s.programmes.filter { p ->
            val modeOk = when (mode) {
                GuideMode.NOW -> p.isOnNow(now)
                GuideMode.TONIGHT -> !p.end.isBefore(eveningStart) && !p.hasEnded(now)
                GuideMode.SEARCH -> q.isNotEmpty()
                GuideMode.ALL -> (!s.onNowOnly || p.isOnNow(now)) && (!(isToday && s.hideEnded) || !p.hasEnded(now))
            }
            modeOk &&
                (q.isEmpty() || p.title.contains(q, true) || p.channelName.contains(q, true) ||
                    p.subtitle?.contains(q, true) == true) &&
                (genre == null || genre in p.genres)
        }
    }

    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar(
            title = mode.title,
            subtitle = "${s.country.name} · ${dateLabel(s.date)}",
            badge = s.country.flag,
            onTitleClick = onCountry,
            actions = { IconButton(onClick = onSources) { Icon(Icons.Filled.Tune, "Guide sources", tint = Snap.BarText) } },
        )

        if (mode == GuideMode.ALL) DateStrip(s.date, vm::setDate)
        if (mode != GuideMode.SEARCH) ViewSwitch(
            grid = s.grid, onGrid = vm::setGrid,
            showNow = useGrid && isTodaySelected(s.date),
            onNow = {
                scope.launch {
                    val minutes = ((Instant.now().epochSecond - gridStart.epochSecond) / 60f - 30f).coerceAtLeast(0f)
                    hScroll.animateScrollTo(with(density) { (3.5.dp * minutes).toPx() }.toInt())
                }
            },
        )
        if (mode == GuideMode.ALL || mode == GuideMode.SEARCH) {
            SearchField(s.query, vm::setQuery, "Search shows or channels",
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp).focusRequester(focus))
        }
        LazyRow(
            Modifier.padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (mode == GuideMode.ALL && !useGrid) {
                if (isToday) {
                    item { Pill("● On now", s.onNowOnly, vm::toggleOnNow) }
                    item { Pill("Hide ended", s.hideEnded, vm::toggleHideEnded) }
                }
                item { Pill(if (s.byChannel) "By channel" else "By time", true, vm::toggleGrouping) }
            }
            items(genres) { g -> Pill(g, s.genre == g) { vm.setGenre(if (s.genre == g) null else g) } }
        }
        SourceStrip(s.feeds, onSources)
        if (s.loading && s.programmes.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Snap.Accent)

        val byChannel = s.byChannel && mode != GuideMode.SEARCH
        when {
            s.programmes.isEmpty() && s.loading -> Loading("Loading ${s.country.name} listings from all sources…")
            s.programmes.isEmpty() && s.allFailed -> CenterMessage(
                "Couldn't load the guide",
                s.feeds.firstOrNull { it.error != null }?.error ?: "Check your connection.",
                "Try again",
            ) { vm.load(true) }
            s.programmes.isEmpty() -> CenterMessage(
                "No listings for ${s.country.name}",
                "None of the guide sources has listings for this country and day. Try another day or country, " +
                    "or add your own XMLTV guide under Sources.",
                "Guide sources",
            ) { onSources() }
            useGrid && gridProgrammes.isEmpty() -> CenterMessage("Nothing matches", "Try clearing the search or genre filter.")
            useGrid -> GuideGrid(
                programmes = gridProgrammes, windowStart = gridStart, windowEnd = gridEnd,
                initialFocus = gridFocus, now = now, favKeys = favKeys, hScroll = hScroll,
                onProgramme = onProgramme,
            )
            mode == GuideMode.SEARCH && s.query.isBlank() -> CenterMessage(
                "Search ${"%,d".format(s.programmes.size)} programmes", "Type a show, film, team or channel name.")
            filtered.isEmpty() -> CenterMessage("Nothing matches", "Try clearing the search or filters.")
            byChannel -> {
                val groups = remember(filtered) {
                    filtered.groupBy { it.channelKey }.values
                        .map { list -> Triple(list.first().channelName, list.firstNotNullOfOrNull { it.channelLogo }, list) }
                        .sortedBy { it.first.lowercase() }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    item { Count(filtered.size, groups.size) }
                    groups.forEachIndexed { gi, (channel, logo, list) ->
                        stickyHeader(key = "h_$gi$channel") { ChannelHeader(channel, logo, list.size) }
                        itemsIndexed(list, key = { _, p -> "g${gi}_${p.id}" }) { i, p ->
                            ProgrammeRow(p, now, showChannel = false, fav = p.favKey in favKeys, index = i) { onProgramme(p) }
                        }
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                item { Count(filtered.size, filtered.map { it.channelKey }.distinct().size) }
                itemsIndexed(filtered, key = { _, p -> p.id }) { i, p ->
                    ProgrammeRow(p, now, showChannel = true, fav = p.favKey in favKeys, index = i) { onProgramme(p) }
                }
            }
        }
    }
}

private fun isTodaySelected(d: LocalDate) = d == LocalDate.now()

/** Segmented List / Grid switch, plus a "Now" jump button in grid view. */
@Composable
private fun ViewSwitch(grid: Boolean, onGrid: (Boolean) -> Unit, showNow: Boolean, onNow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Snap.Row)
                .border(1.dp, Snap.Line, RoundedCornerShape(8.dp)),
        ) {
            listOf(false to "☰  List", true to "▦  Grid").forEach { (isGrid, label) ->
                val on = grid == isGrid
                Box(
                    Modifier.background(if (on) Snap.accentBar else SolidColor(Color.Transparent))
                        .clickable { onGrid(isGrid) }.padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(label, color = if (on) Snap.OnAccent else Snap.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (showNow) Pill("⏱ Jump to now", false, onNow)
    }
}

private fun dateLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> DateTimeFormatter.ofPattern("EEE d MMM").format(d)
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (on) Snap.accentBar else SolidColor(Snap.Row))
            .border(1.dp, if (on) Color.Transparent else Snap.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (on) Snap.OnAccent else Snap.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** One small chip per guide file, showing whether it loaded and how many programmes it added. */
@Composable
private fun SourceStrip(feeds: List<FeedResult>, onClick: () -> Unit) {
    if (feeds.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(feeds, key = { it.key }) { f ->
            val (mark, color) = when (f.status) {
                FeedStatus.LOADING -> "…" to Snap.Muted
                FeedStatus.DONE -> if (f.emptyForDay) "⚠ ${f.staleNote}" to Snap.Muted
                    else "✓ %,d".format(f.programmes.size) to Snap.Ok
                FeedStatus.FAILED -> "✕" to Snap.Bad
            }
            Text(
                "${if (f.label == f.sourceName) f.label else "${f.sourceName} ${f.label}"} $mark",
                Modifier.clip(RoundedCornerShape(4.dp)).background(Snap.RowAlt).clickable(onClick = onClick)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                fontSize = 10.sp, color = color, maxLines = 1,
            )
        }
    }
}

@Composable
private fun Count(programmes: Int, channels: Int) {
    Text("%,d programmes · %,d channels · your time zone".format(programmes, channels),
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp, color = Snap.Muted)
}

@Composable
private fun DateStrip(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = remember(today) { (-1..6).map { today.plusDays(it.toLong()) } }
    val dow = DateTimeFormatter.ofPattern("EEE")
    Row(Modifier.fillMaxWidth().background(Snap.RowAlt)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(days) { d ->
                val sel = d == selected
                val label = when (d) { today -> "Today"; today.minusDays(1) -> "Yest."; today.plusDays(1) -> "Tmrw"; else -> dow.format(d) }
                Column(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (sel) Snap.accentBar else SolidColor(Snap.Row))
                        .clickable { onPick(d) }.padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, fontSize = 11.sp, color = if (sel) Snap.OnAccent else Snap.Muted, fontWeight = FontWeight.Bold)
                    Text("${d.dayOfMonth}", fontSize = 16.sp, color = if (sel) Snap.OnAccent else Snap.Text, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(name: String, logo: String?, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(Snap.section).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(Snap.Well),
            contentAlignment = Alignment.Center) {
            Text(name.take(2).uppercase(), color = Snap.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (logo != null) AsyncImage(logo, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(10.dp))
        Text(name, Modifier.weight(1f), color = Snap.SectionText, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            lineHeight = 17.sp)
        Text("$count", color = Snap.SectionText.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
fun ProgrammeRow(p: Programme, now: Instant, showChannel: Boolean, fav: Boolean, index: Int = 0, onClick: () -> Unit) {
    val live = p.isOnNow(now)
    val ended = p.hasEnded(now)
    Row(
        Modifier.fillMaxWidth()
            .background(if (live) Snap.LiveRow else if (index % 2 == 0) Snap.Row else Snap.RowAlt)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(58.dp)) {
            Text(p.start.localTime(), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (live) Snap.Accent else if (ended) Snap.Muted else Snap.Text)
            if (live) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Snap.Live))
                Spacer(Modifier.width(3.dp))
                Text("LIVE", fontSize = 10.sp, color = Snap.Live, fontWeight = FontWeight.Bold)
            }
        }
        val img = p.image ?: p.channelLogo.takeIf { showChannel }
        if (img != null) {
            Box(Modifier.size(width = 40.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Snap.Well)) {
                AsyncImage(img, null, Modifier.fillMaxSize(),
                    contentScale = if (p.image != null) ContentScale.Crop else ContentScale.Fit)
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) {
                    Icon(Icons.Filled.Star, "Following", Modifier.size(14.dp), tint = Snap.Accent)
                    Spacer(Modifier.width(3.dp))
                }
                Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (ended) Snap.Muted else Snap.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showChannel) Text(p.channelName, fontSize = 12.sp, lineHeight = 15.sp, color = Snap.Accent,
                fontWeight = FontWeight.Bold)
            val sub = listOfNotNull(p.subtitle, p.genres.firstOrNull()).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Snap.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Short description preview; the full text is on the programme page.
            p.description?.takeIf { it.isNotBlank() }?.let { d ->
                Text(d, fontSize = 12.sp, lineHeight = 15.sp, color = Snap.Text.copy(alpha = 0.72f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            if (live) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { p.progress(now) }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Snap.Accent, trackColor = Snap.Line,
                )
            }
        }
    }
}

