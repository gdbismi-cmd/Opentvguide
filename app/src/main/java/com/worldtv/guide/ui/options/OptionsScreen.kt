package com.worldtv.guide.ui.options

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.guide.ui.components.SnapIcon
import com.worldtv.guide.ui.components.SnapMenuRow
import com.worldtv.guide.ui.components.SnapSection
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.guide.GuideViewModel
import com.worldtv.guide.ui.theme.Snap

/** Left-soft-key "Options" menu, like a classic portal app. */
@Composable
fun OptionsScreen(vm: GuideViewModel, onThemes: () -> Unit, onSources: () -> Unit, onCountry: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var about by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar("Options", subtitle = "WorldTV Guide", badge = "☰")
        LazyColumn(Modifier.fillMaxSize()) {
            item { SnapSection("Settings") }
            item {
                SnapMenuRow("Themes", "Skin: ${Snap.palette.name}", index = 0,
                    leading = { SnapIcon(Icons.Filled.Palette, Color(0xFFE91E63)) }, onClick = onThemes)
            }
            item {
                SnapMenuRow("Country", "${s.country.flag} ${s.country.name}", index = 1,
                    leading = { SnapIcon(Icons.Filled.Public, Color(0xFF8E24AA)) }, onClick = onCountry)
            }
            item {
                SnapMenuRow("Guide sources", "Turn sources on/off, add XMLTV links", index = 2,
                    leading = { SnapIcon(Icons.Filled.Tune, Color(0xFF546E7A)) }, onClick = onSources)
            }
            item { SnapSection("Data") }
            item {
                SnapMenuRow("Refresh listings", "Download the latest guide data now", index = 0,
                    leading = { SnapIcon(Icons.Filled.Refresh, Color(0xFF43A047)) }) { vm.load(force = true) }
            }
            item {
                SnapMenuRow("About", "Version 1.0 · data credits", index = 1,
                    leading = { SnapIcon(Icons.Filled.Info, Color(0xFF3949AB)) }) { about = true }
            }
        }
    }

    if (about) AlertDialog(
        onDismissRequest = { about = false },
        confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } },
        title = { Text("WorldTV Guide") },
        text = {
            Text(
                "A worldwide TV guide.\n\nListings: TVmaze (CC BY-SA), epgshare01, epg.pw, GlobeTV EPG, i.mjh.nz, " +
                    "plus any XMLTV guides you add under Sources.\nCountry list: iptv-org.",
            )
        },
    )
}
