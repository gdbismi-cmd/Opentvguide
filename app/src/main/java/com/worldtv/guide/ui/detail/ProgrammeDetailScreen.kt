package com.worldtv.guide.ui.detail

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.worldtv.guide.data.TvRepository
import com.worldtv.guide.ui.components.CenterMessage
import com.worldtv.guide.ui.theme.LiveRed
import com.worldtv.guide.util.localDateTime
import com.worldtv.guide.util.localTime
import java.time.Instant

/** Detail page for a programme that came from an XMLTV guide. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgrammeDetailScreen(id: String, repo: TvRepository, prefs: Prefs, onBack: () -> Unit) {
    val p = repo.programme(id)
    val favShows by prefs.favShows.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val following = p != null && favShows.any { it.key == p.favKey }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(p?.title ?: "Programme", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (p != null) IconButton(onClick = { prefs.toggleShow(FavShow(p.favKey, p.title, p.image)) }) {
                    Icon(if (following) Icons.Filled.Star else Icons.Filled.StarBorder, "Follow show",
                        tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            },
        )
        if (p == null) {
            CenterMessage("Programme no longer loaded", "Go back and open it again from the guide.")
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (p.image != null) {
                AsyncImage(p.image, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(20.dp)) {
                Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                p.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Text(p.channelName.take(2).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (p.channelLogo != null) AsyncImage(p.channelLogo, null, Modifier.fillMaxSize().padding(3.dp),
                            contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.channelName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("${p.start.localDateTime()} – ${p.end.localTime()} · ${p.durationMin} min",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (p.isOnNow()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(LiveRed).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("ON NOW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface)
                        }
                        Spacer(Modifier.width(10.dp))
                        LinearProgressIndicator(progress = { p.progress() }, modifier = Modifier.weight(1f))
                    }
                }
                if (p.genres.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.genres.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
                p.description?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(24.dp))
                if (p.end.isAfter(Instant.now())) {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, "📺 ${p.title}" + (p.subtitle?.let { " ($it)" } ?: ""))
                            .putExtra(CalendarContract.Events.EVENT_LOCATION, p.channelName)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, p.start.toEpochMilli())
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, p.end.toEpochMilli())
                            .putExtra(CalendarContract.Events.DESCRIPTION, p.description)
                        runCatching { ctx.startActivity(intent) }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Remind me (add to calendar)")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Listing from ${p.sourceName}.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
