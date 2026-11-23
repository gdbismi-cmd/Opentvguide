package com.worldtv.guide.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.STREAMING
import com.worldtv.guide.ui.components.SnapTile
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.guide.GuideMode
import com.worldtv.guide.ui.guide.GuideViewModel
import com.worldtv.guide.ui.theme.Snap
import kotlinx.coroutines.delay
import java.time.Instant

private data class Tile(val label: String, val icon: ImageVector, val color: Color, val badge: String?, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    vm: GuideViewModel, prefs: Prefs,
    onGuide: (GuideMode) -> Unit, onFollowing: () -> Unit, onCountry: () -> Unit, onSources: () -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favShows by prefs.favShows.collectAsStateWithLifecycle()
    var about by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Instant.now() } }

    val liveCount = remember(s.programmes, now) { s.programmes.count { it.isOnNow(now) } }
    val favKeys = remember(favShows) { favShows.map { it.key }.toSet() }
    val favToday = remember(s.programmes, favKeys, now) {
        s.programmes.count { it.favKey in favKeys && !it.hasEnded(now) }
    }
    val nowPlaying = remember(s.programmes, now) {
        s.programmes.filter { it.isOnNow(now) }.shuffled(kotlin.random.Random(now.epochSecond / 30)).take(1).firstOrNull()
    }

    val tiles = listOf(
        Tile("On Now", Icons.Filled.LiveTv, Color(0xFFE53935), liveCount.takeIf { it > 0 }?.let(::short)) { onGuide(GuideMode.NOW) },
        Tile("Tonight", Icons.Filled.Bedtime, Color(0xFF5E35B1), null) { onGuide(GuideMode.TONIGHT) },
        Tile("Full Guide", Icons.Filled.ViewList, Color(0xFF1E88E5), null) { onGuide(GuideMode.ALL) },
        Tile("Following", Icons.Filled.Star, Color(0xFFFB8C00), favToday.takeIf { it > 0 }?.toString()) { onFollowing() },
        Tile("Search", Icons.Filled.Search, Color(0xFF43A047), null) { onGuide(GuideMode.SEARCH) },
        Tile("Streaming", Icons.Filled.Language, Color(0xFF00897B), null) {
            if (s.country.code != STREAMING.code) vm.setCountry(STREAMING)
            onGuide(GuideMode.ALL)
        },
        Tile("Country", Icons.Filled.Public, Color(0xFF8E24AA), null) { onCountry() },
        Tile("Sources", Icons.Filled.Tune, Color(0xFF546E7A), null) { onSources() },
        Tile("About", Icons.Filled.Info, Color(0xFF3949AB), null) { about = true },
    )

    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar("WorldTV Guide", subtitle = "${s.country.flag} ${s.country.name}", badge = "📺", onTitleClick = onCountry)

        // "Now showing" ticker strip, like a portal news line.
        Row(
            Modifier.fillMaxWidth().background(Snap.Row).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.loading) Snap.Muted else Snap.Live))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    s.loading && s.programmes.isEmpty() -> "Loading listings…"
                    nowPlaying != null -> "NOW  ${nowPlaying.title}  ·  ${nowPlaying.channelName}"
                    s.programmes.isEmpty() -> "No listings for this country yet. Try Sources"
                    else -> "%,d programmes today".format(s.programmes.size)
                },
                color = Snap.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(tiles, key = { it.label }) { t -> SnapTile(t.label, t.icon, t.color, t.badge, t.onClick) }
            item(span = { GridItemSpan(3) }) {
                Text(
                    "Listings from TVmaze, epgshare01, GlobeTV EPG and more · times in your time zone",
                    color = Snap.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (about) AlertDialog(
        onDismissRequest = { about = false },
        confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } },
        title = { Text("WorldTV Guide") },
        text = {
            Text(
                "A worldwide TV guide.\n\nListings: TVmaze (CC BY-SA), epgshare01, GlobeTV EPG, i.mjh.nz, " +
                    "plus any XMLTV guides you add under Sources.\nCountry list: iptv-org.",
            )
        },
    )
}

private fun short(n: Int): String = if (n >= 1000) "${n / 1000}k" else n.toString()
