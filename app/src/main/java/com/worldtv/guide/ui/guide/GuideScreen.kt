package com.worldtv.guide.ui.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.worldtv.guide.data.FeedResult
import com.worldtv.guide.data.FeedStatus
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.Programme
import com.worldtv.guide.ui.components.CenterMessage
import com.worldtv.guide.ui.components.CountryPickerSheet
import com.worldtv.guide.ui.components.Loading
import com.worldtv.guide.ui.components.SearchField
import com.worldtv.guide.ui.theme.LiveRed
import com.worldtv.guide.util.localTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GuideScreen(vm: GuideViewModel, prefs: Prefs, onProgramme: (Programme) -> Unit, onSources: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favShows by prefs.favShows.collectAsStateWithLifecycle()
    val favKeys = remember(favShows) { favShows.map { it.key }.toSet() }
    var picking by remember { mutableStateOf(false) }

    // Tick every 30 s so "on now" badges and progress bars stay current.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Instant.now() } }

    val genres = remember(s.programmes) {
        s.programmes.flatMap { it.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(30).map { it.key }
    }
    val isToday = s.date == LocalDate.now()
    val filtered = remember(s.programmes, s.query, s.genre, s.onNowOnly, s.hideEnded, isToday, now) {
        val q = s.query.trim()
        val genre: String? = s.genre
        val onNowOnly = s.onNowOnly
        val hideEnded = isToday && s.hideEnded
        s.programmes.filter { p ->
            (q.isEmpty() || p.title.contains(q, true) || p.channelName.contains(q, true) ||
                p.subtitle?.contains(q, true) == true) &&
                (genre == null || genre in p.genres) &&
                (!onNowOnly || p.isOnNow(now)) &&
                (!hideEnded || !p.hasEnded(now))
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { picking = true }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.country.flag, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text("TV Guide", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.country.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Filled.ArrowDropDown, "Change country")
                }
            },
            actions = {
                IconButton(onClick = onSources) { Icon(Icons.Filled.Tune, "Guide sources") }
                IconButton(onClick = { vm.load(force = true) }) { Icon(Icons.Filled.Refresh, "Refresh") }
            },
        )

        DateStrip(s.date, vm::setDate)
        SearchField(s.query, vm::setQuery, "Search shows or channels",
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isToday) {
                item { FilterChip(s.onNowOnly, vm::toggleOnNow, label = { Text("● On now") }) }
                item { FilterChip(s.hideEnded, vm::toggleHideEnded, label = { Text("Hide ended") }) }
            }
            item {
                AssistChip(onClick = vm::toggleGrouping,
                    label = { Text(if (s.byChannel) "Grouped by channel" else "Sorted by time") })
            }
            items(genres) { g -> FilterChip(s.genre == g, { vm.setGenre(if (s.genre == g) null else g) }, label = { Text(g) }) }
        }
        SourceStrip(s.feeds, onSources)
        if (s.loading && s.programmes.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))

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
                    "add your own XMLTV guide under Sources, or browse this country's channels in the Channels tab.",
                "Guide sources",
            ) { onSources() }
            filtered.isEmpty() -> CenterMessage("Nothing matches", "Try clearing the search or filters.")
            s.byChannel -> {
                val groups = remember(filtered) {
                    filtered.groupBy { it.channelKey }.values
                        .map { list -> Triple(list.first().channelName, list.firstNotNullOfOrNull { it.channelLogo }, list) }
                        .sortedBy { it.first.lowercase() }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { Count(filtered.size, groups.size) }
                    groups.forEachIndexed { gi, (channel, logo, list) ->
                        stickyHeader(key = "h_$gi$channel") { ChannelHeader(channel, logo, list.size) }
                        items(list, key = { "g${gi}_${it.id}" }) { p ->
                            ProgrammeRow(p, now, showChannel = false, fav = p.favKey in favKeys) { onProgramme(p) }
                        }
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { Count(filtered.size, filtered.map { it.channelKey }.distinct().size) }
                items(filtered, key = { it.id }) { p ->
                    ProgrammeRow(p, now, showChannel = true, fav = p.favKey in favKeys) { onProgramme(p) }
                }
            }
        }
    }

    if (picking) {
        CountryPickerSheet(
            countries = s.countries, selectedCode = s.country.code,
            onPick = { c -> picking = false; if (c != null) vm.setCountry(c) },
            onDismiss = { picking = false },
        )
    }
}

/** One small chip per guide file, showing whether it loaded and how many programmes it added. */
@Composable
private fun SourceStrip(feeds: List<FeedResult>, onClick: () -> Unit) {
    if (feeds.isEmpty()) return
    LazyRow(
        Modifier.padding(top = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(feeds, key = { it.key }) { f ->
            val (mark, color) = when (f.status) {
                FeedStatus.LOADING -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
                FeedStatus.DONE -> "✓ %,d".format(f.programmes.size) to MaterialTheme.colorScheme.secondary
                FeedStatus.FAILED -> "✕" to MaterialTheme.colorScheme.error
            }
            Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "${if (f.label == f.sourceName) f.label else "${f.sourceName} ${f.label}"} $mark",
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Count(programmes: Int, channels: Int) {
    Text("%,d programmes on %,d channels · times in your time zone".format(programmes, channels),
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DateStrip(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = remember(today) { (-1..6).map { today.plusDays(it.toLong()) } }
    val dow = DateTimeFormatter.ofPattern("EEE")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { d ->
            val sel = d == selected
            val label = when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; today.plusDays(1) -> "Tomorrow"; else -> dow.format(d) }
            Surface(
                onClick = { onPick(d) },
                shape = RoundedCornerShape(14.dp),
                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(name: String, logo: String?, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            Text(name.take(2).uppercase(), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (logo != null) AsyncImage(logo, null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(12.dp))
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProgrammeRow(p: Programme, now: Instant, showChannel: Boolean, fav: Boolean, onClick: () -> Unit) {
    val live = p.isOnNow(now)
    val ended = p.hasEnded(now)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(64.dp)) {
                Text(p.start.localTime(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = if (ended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (live) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(LiveRed))
                    Spacer(Modifier.width(4.dp))
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = LiveRed, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.size(width = 44.dp, height = 60.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val img = p.image ?: p.channelLogo.takeIf { showChannel }
                if (img != null) AsyncImage(img, null, Modifier.fillMaxSize(),
                    contentScale = if (p.image != null) ContentScale.Crop else ContentScale.Fit)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fav) {
                        Icon(Icons.Filled.Star, "Following", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(p.title, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val sub = listOfNotNull(
                    p.channelName.takeIf { showChannel },
                    p.subtitle,
                    p.genres.firstOrNull(),
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (live) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { p.progress(now) }, modifier = Modifier.fillMaxWidth().height(3.dp))
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 136.dp), thickness = 0.5.dp)
    }
}
