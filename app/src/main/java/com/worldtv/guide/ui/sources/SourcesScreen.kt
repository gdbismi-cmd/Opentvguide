package com.worldtv.guide.ui.sources

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.guide.data.BUILT_IN_SOURCES
import com.worldtv.guide.data.Country
import com.worldtv.guide.data.FeedResult
import com.worldtv.guide.data.FeedStatus
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.ui.components.CountryPickerSheet
import com.worldtv.guide.ui.components.SnapIcon
import com.worldtv.guide.ui.components.SnapMenuRow
import com.worldtv.guide.ui.components.SnapSection
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.theme.Snap
import com.worldtv.guide.ui.guide.GuideViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(guideVm: GuideViewModel, prefs: Prefs) {
    val guide by guideVm.state.collectAsStateWithLifecycle()
    val disabled by prefs.disabledSources.collectAsStateWithLifecycle()
    val custom by prefs.customSources.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar("Guide sources", subtitle = "${guide.country.flag} ${guide.country.name}", badge = "⚙️")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Text(
                    "Listings from every source that's switched on are merged into one guide, with duplicates removed.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SnapSection("Built-in sources") }
            items(BUILT_IN_SOURCES, key = { it.id }) { src ->
                val feeds = guide.feeds.filter { it.sourceId == src.id }
                SourceCard(
                    title = src.name, body = src.description,
                    status = statusLine(feeds, guide.country),
                    enabled = src.id !in disabled,
                    onToggle = { prefs.setSourceEnabled(src.id, it) },
                    extra = {
                        TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.url))) } }) {
                            Text("Website")
                        }
                    },
                )
            }
            item { SnapSection("Your XMLTV guides", "${custom.size}") }
            item {
                SnapMenuRow(
                    title = "Add XMLTV guide…", subtitle = "Paste a .xml or .xml.gz guide link",
                    leading = { SnapIcon(Icons.Filled.Add, Snap.AccentDeep) },
                ) { adding = true }
            }
            if (custom.isEmpty()) item {
                Text(
                    "Add any XMLTV guide URL (.xml or .xml.gz), for example from your IPTV provider, " +
                        "open-epg.com or epg.pw. You can limit it to one country or show it everywhere.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(custom, key = { it.id }) { c ->
                val id = "custom:${c.id}"
                val feeds = guide.feeds.filter { it.sourceId == id }
                val where = c.country?.let { code -> guide.countries.firstOrNull { it.code == code }?.let { "${it.flag} ${it.name}" } ?: code }
                    ?: "All countries"
                SourceCard(
                    title = c.name, body = "${c.url}\nShown for: $where",
                    status = statusLine(feeds, guide.country),
                    enabled = id !in disabled,
                    onToggle = { prefs.setSourceEnabled(id, it) },
                    extra = {
                        IconButton(onClick = { prefs.removeCustomSource(c.id) }) { Icon(Icons.Filled.Delete, "Remove") }
                    },
                )
            }
        }
    }

    if (adding) AddSourceDialog(
        countries = guide.countries, current = guide.country,
        onDismiss = { adding = false },
        onAdd = { name, url, country -> prefs.addCustomSource(name, url, country); adding = false },
    )
}

private fun statusLine(feeds: List<FeedResult>, country: Country): String? {
    if (feeds.isEmpty()) return null
    return "${country.flag} " + feeds.joinToString("  ·  ") { f ->
        when {
            f.status == FeedStatus.LOADING -> "${f.label}: loading…"
            f.status == FeedStatus.FAILED -> "${f.label}: failed (${f.error ?: "unknown error"})"
            f.emptyForDay -> "${f.label}: ${f.staleNote}"
            else -> "${f.label}: %,d programmes".format(f.programmes.size)
        }
    }
}

@Composable
private fun SourceCard(
    title: String, body: String, status: String?, enabled: Boolean,
    onToggle: (Boolean) -> Unit, extra: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Snap.Row),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = enabled, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = Snap.Accent, checkedThumbColor = Snap.OnAccent),
                )
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (status != null && enabled) {
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { extra() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceDialog(
    countries: List<Country>, current: Country,
    onDismiss: () -> Unit, onAdd: (name: String, url: String, country: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<Country?>(current) }
    var picking by remember { mutableStateOf(false) }
    val valid = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add XMLTV guide") },
        text = {
            Column {
                OutlinedTextField(url, { url = it }, label = { Text("Guide URL (.xml or .xml.gz)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Show this guide for", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(country?.let { "${it.flag} ${it.name}" } ?: "🗺️ All countries", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val u = url.trim()
                val n = name.ifBlank { Uri.parse(u).host ?: "My guide" }
                onAdd(n, u, country?.code)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) CountryPickerSheet(
        countries = countries, selectedCode = country?.code, allOption = "All countries",
        onPick = { country = it; picking = false }, onDismiss = { picking = false },
    )
}

