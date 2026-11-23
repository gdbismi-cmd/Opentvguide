package com.worldtv.guide.ui.detail

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.theme.Snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.worldtv.guide.data.FavShow
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.TmEpisode
import com.worldtv.guide.data.TvRepository
import com.worldtv.guide.ui.components.CenterMessage
import com.worldtv.guide.ui.components.Loading
import com.worldtv.guide.ui.theme.LiveRed
import com.worldtv.guide.util.durationMin
import com.worldtv.guide.util.endInstant
import com.worldtv.guide.util.episodeLabel
import com.worldtv.guide.util.isOnNow
import com.worldtv.guide.util.localDateTime
import com.worldtv.guide.util.localTime
import com.worldtv.guide.util.progress
import com.worldtv.guide.util.startInstant
import com.worldtv.guide.util.stripHtml

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeDetailScreen(id: Long, repo: TvRepository, prefs: Prefs) {
    var ep by remember { mutableStateOf<TmEpisode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val favShows by prefs.favShows.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(id) {
        runCatching { repo.episode(id) }.onSuccess { ep = it }.onFailure { error = it.message ?: "Couldn't load" }
    }

    val e = ep
    val show = e?.theShow
    val isFav = show != null && favShows.any { it.key == "tm:${show.id}" }

    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar(
            title = show?.name ?: "Programme",
            subtitle = e?.channel?.name,
            actions = {
                if (show != null) IconButton(onClick = {
                    prefs.toggleShow(FavShow("tm:${show.id}", show.name, show.image?.medium))
                }) {
                    Icon(if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite show",
                        tint = if (isFav) Snap.Accent else Snap.Text)
                }
            },
        )
        when {
            error != null -> CenterMessage("Couldn't load programme", error)
            e == null -> Loading("Loading…")
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val img = e.image?.original ?: show?.image?.original
                if (img != null) {
                    AsyncImage(
                        model = img, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Column(Modifier.padding(20.dp)) {
                    Text(show?.name ?: e.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val label = e.episodeLabel()
                    if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))

                    val start = e.startInstant
                    val live = e.isOnNow()
                    Row {
                        if (live) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(LiveRed).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("ON NOW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.surface)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            listOfNotNull(
                                start?.let { "${it.localDateTime()} – ${e.endInstant?.localTime()}" },
                                "${e.durationMin} min",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (live) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { e.progress() }, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    val ch = e.channel
                    Text(
                        listOfNotNull(ch?.name, ch?.country?.name, show?.language).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    show?.rating?.average?.let {
                        Text("★ $it / 10", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!show?.genres.isNullOrEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            show!!.genres.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                        }
                    }

                    val epSummary = e.summary.stripHtml()
                    val showSummary = show?.summary.stripHtml()
                    if (epSummary.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text("This episode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(epSummary, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (showSummary.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text("About the show", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(showSummary, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(Modifier.height(24.dp))
                    if (start != null && e.endInstant?.isAfter(java.time.Instant.now()) == true) {
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                                .putExtra(CalendarContract.Events.TITLE, "📺 ${show?.name ?: e.name}" +
                                    (label.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
                                .putExtra(CalendarContract.Events.EVENT_LOCATION, ch?.name)
                                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
                                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.endInstant!!.toEpochMilli())
                                .putExtra(CalendarContract.Events.DESCRIPTION, epSummary.ifBlank { showSummary })
                            runCatching { ctx.startActivity(intent) }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Remind me (add to calendar)")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    val site = show?.officialSite ?: ch?.officialSite
                    if (site != null) {
                        OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site))) } },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("Official site")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Listing data from TVmaze.com (CC BY-SA).", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
