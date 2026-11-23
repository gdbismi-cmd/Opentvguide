package com.worldtv.guide.ui.channels

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.worldtv.guide.data.Channel
import com.worldtv.guide.data.Directory
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.ui.components.CenterMessage
import com.worldtv.guide.ui.components.CountryPickerSheet
import com.worldtv.guide.ui.components.Loading
import com.worldtv.guide.ui.components.SearchField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(vm: ChannelsViewModel, prefs: Prefs, onChannel: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by prefs.favChannels.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val dir = s.directory
    val country = s.countryCode?.let { dir?.countryByCode?.get(it) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = dir != null) { picking = true }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(country?.flag ?: "🗺️", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text("World channels", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(country?.name ?: "All countries", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Filled.ArrowDropDown, "Change country")
                }
            },
            actions = { IconButton(onClick = { vm.load(force = true) }) { Icon(Icons.Filled.Refresh, "Refresh") } },
        )

        when {
            s.loading && dir == null -> Loading("Downloading the worldwide channel directory…\nThis takes a moment the first time, then it's cached.")
            s.error != null && dir == null -> CenterMessage("Couldn't load channels", s.error, "Try again") { vm.load(true) }
            dir != null -> {
                SearchField(s.query, vm::setQuery, "Search ${if (country != null) country.name else "all"} channels",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.categoriesHere) { c ->
                        FilterChip(s.category == c, { vm.setCategory(if (s.category == c) null else c) }, label = { Text(c) })
                    }
                }
                Text(
                    "%,d channels".format(s.results.size) + if (country == null) " in ${dir.countries.size} countries" else "",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.results.isEmpty()) CenterMessage("No channels found", "Try another search, category or country.")
                else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(s.results, key = { it.id }) { ch ->
                        ChannelRow(ch, dir, showCountry = country == null, fav = ch.id in favs,
                            onFav = { prefs.toggleChannel(ch.id) }, onClick = { onChannel(ch.id) })
                    }
                }
            }
        }
    }

    if (picking && dir != null) {
        val counts = remember(dir) { dir.channelsByCountry.mapValues { it.value.size } }
        CountryPickerSheet(
            countries = dir.countries.filter { (counts[it.code] ?: 0) > 0 },
            selectedCode = s.countryCode, allOption = "All countries", counts = counts,
            onPick = { c -> picking = false; vm.setCountry(c?.code) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
fun ChannelLogo(ch: Channel, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Text(ch.name.take(2).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        if (ch.logo != null) {
            SubcomposeAsyncImage(
                model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit,
                modifier = Modifier.padding(4.dp).fillMaxSize(),
                loading = { fallback() }, error = { fallback() },
            )
        } else fallback()
    }
}

@Composable
fun ChannelRow(ch: Channel, dir: Directory, showCountry: Boolean, fav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(ch)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val c = dir.countryByCode[ch.countryCode]
            val sub = listOfNotNull(
                c?.let { if (showCountry) "${it.flag} ${it.name}" else null } ?: ch.city,
                ch.categories.takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFav) {
            Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite",
                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 78.dp), thickness = 0.5.dp)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChannelDetailScreen(
    id: String, vm: ChannelsViewModel, prefs: Prefs,
    onBack: () -> Unit, onFindInGuide: (countryCode: String, name: String) -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by prefs.favChannels.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val dir = s.directory
    val ch = dir?.channelById?.get(id)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(ch?.name ?: "Channel", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (ch != null) IconButton(onClick = { prefs.toggleChannel(ch.id) }) {
                    Icon(if (ch.id in favs) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite",
                        tint = if (ch.id in favs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            },
        )
        if (ch == null || dir == null) {
            if (s.loading) Loading("Loading channel…") else CenterMessage("Channel not found")
            return@Column
        }
        val country = dir.countryByCode[ch.countryCode]
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(ch, size = 88)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(ch.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (country != null) Text("${country.flag} ${country.name}" + (ch.city?.let { " · $it" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (ch.categories.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ch.categories.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Fact("Also known as", ch.altNames.joinToString(", "))
            Fact("Network", ch.network)
            Fact("Owner", ch.owners.joinToString(", "))
            Fact("On air since", ch.launched)
            Fact("Channel ID", ch.id)

            Spacer(Modifier.height(24.dp))
            Button(onClick = { onFindInGuide(ch.countryCode, ch.name) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.LiveTv, null); Spacer(Modifier.width(8.dp)); Text("Find in TV guide")
            }
            ch.website?.let { url ->
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("Visit website")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Listings come from TVmaze and may not include every channel. Channel data: iptv-org.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Fact(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
