package com.worldtv.guide.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.Programme
import com.worldtv.guide.ui.components.CenterMessage
import com.worldtv.guide.ui.components.SnapMenuRow
import com.worldtv.guide.ui.components.SnapSection
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.guide.GuideViewModel
import com.worldtv.guide.ui.guide.ProgrammeRow
import com.worldtv.guide.ui.theme.Snap
import java.time.Instant

@Composable
fun FavoritesScreen(
    guideVm: GuideViewModel, prefs: Prefs,
    onProgramme: (Programme) -> Unit, onShowInGuide: (String) -> Unit,
) {
    val guide by guideVm.state.collectAsStateWithLifecycle()
    val favShows by prefs.favShows.collectAsStateWithLifecycle()
    val now = remember { Instant.now() }
    val showKeys = remember(favShows) { favShows.map { it.key }.toSet() }
    val upcoming = remember(guide.programmes, showKeys) {
        guide.programmes.filter { it.favKey in showKeys && !it.hasEnded(now) }
    }

    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar("Following", subtitle = "${favShows.size} shows", badge = "⭐")
        if (favShows.isEmpty()) {
            CenterMessage("You're not following any shows",
                "Open any programme in the guide and tap ☆ to follow it. Its next airings will appear here.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            item { SnapSection("Coming up · ${guide.country.flag} ${guide.country.name}", "${upcoming.size}") }
            if (upcoming.isEmpty()) item {
                Text("None of your shows air here on this day.", Modifier.padding(12.dp), color = Snap.Muted, fontSize = 13.sp)
            }
            itemsIndexed(upcoming, key = { _, p -> "u${p.id}" }) { i, p ->
                ProgrammeRow(p, now, showChannel = true, fav = true, index = i) { onProgramme(p) }
            }
            item { SnapSection("Shows you follow", "${favShows.size}") }
            itemsIndexed(favShows, key = { _, f -> "s${f.key}" }) { i, f ->
                SnapMenuRow(
                    title = f.name, subtitle = "Find in guide", index = i,
                    leading = {
                        Box(Modifier.size(34.dp, 46.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0E1118))) {
                            if (f.image != null) AsyncImage(f.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    },
                    trailing = {
                        IconButton(onClick = { prefs.toggleShow(f) }) { Icon(Icons.Filled.Star, "Unfollow", tint = Snap.Accent) }
                    },
                ) { onShowInGuide(f.name) }
            }
        }
    }
}
