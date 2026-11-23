package com.worldtv.guide.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.worldtv.guide.ui.channels.ChannelRow
import com.worldtv.guide.ui.channels.ChannelsViewModel
import com.worldtv.guide.ui.components.CenterMessage
import com.worldtv.guide.ui.components.SectionHeader
import com.worldtv.guide.ui.guide.GuideViewModel
import com.worldtv.guide.ui.guide.ProgrammeRow
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.util.hasEnded
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    guideVm: GuideViewModel, channelsVm: ChannelsViewModel, prefs: Prefs,
    onChannel: (String) -> Unit, onEpisode: (Long) -> Unit, onShowInGuide: (String) -> Unit,
) {
    val guide by guideVm.state.collectAsStateWithLifecycle()
    val ch by channelsVm.state.collectAsStateWithLifecycle()
    val favChannels by prefs.favChannels.collectAsStateWithLifecycle()
    val favShows by prefs.favShows.collectAsStateWithLifecycle()
    val now = remember { Instant.now() }
    val showIds = remember(favShows) { favShows.map { it.id }.toSet() }
    val upcoming = remember(guide.episodes, showIds) {
        guide.episodes.filter { it.theShow?.id in showIds && !it.hasEnded(now) }
    }
    val dir = ch.directory
    val channels = remember(dir, favChannels) {
        dir?.let { d -> favChannels.mapNotNull { d.channelById[it] }.sortedBy { it.name.lowercase() } }.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Favourites") })
        if (favShows.isEmpty() && favChannels.isEmpty()) {
            CenterMessage("No favourites yet",
                "Tap ☆ on a programme to follow its show, or on any channel in the Channels tab.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (favShows.isNotEmpty()) {
                item { SectionHeader("Coming up in ${guide.country.flag} ${guide.country.name} · ${guide.date}") }
                if (upcoming.isEmpty()) item {
                    Text("None of your shows air here on this day.",
                        Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(upcoming, key = { "u${it.id}" }) { e ->
                    ProgrammeRow(e, now, showChannel = true, fav = true) { onEpisode(e.id) }
                }
                item { SectionHeader("Shows you follow") }
                items(favShows, key = { "s${it.id}" }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onShowInGuide(s.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(s.image, null, Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant))
                        Spacer(Modifier.width(14.dp))
                        Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { prefs.toggleShow(s) }) {
                            Icon(Icons.Filled.Star, "Unfollow", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (favChannels.isNotEmpty()) {
                item { SectionHeader("Favourite channels") }
                if (dir == null) item {
                    Text("Loading channel directory…", Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else items(channels, key = { "c${it.id}" }) { c ->
                    ChannelRow(c, dir, showCountry = true, fav = true,
                        onFav = { prefs.toggleChannel(c.id) }, onClick = { onChannel(c.id) })
                }
            }
        }
    }
}
