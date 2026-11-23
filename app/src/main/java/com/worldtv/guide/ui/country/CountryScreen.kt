package com.worldtv.guide.ui.country

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.guide.data.STREAMING
import com.worldtv.guide.ui.components.SearchField
import com.worldtv.guide.ui.components.SnapMenuRow
import com.worldtv.guide.ui.components.SnapSection
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.guide.GuideViewModel
import com.worldtv.guide.ui.theme.Snap

/** Full-screen country menu. */
@Composable
fun CountryScreen(vm: GuideViewModel, onPicked: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val list = remember(q, s.countries) {
        val all = s.countries
        if (q.isBlank()) all else all.filter { it.name.contains(q, true) || it.code.equals(q.trim(), true) }
    }

    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar("Choose country", subtitle = "${s.countries.size} countries", badge = "🌍")
        SearchField(q, { q = it }, "Search countries", Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (q.isBlank()) item { SnapSection("Streaming") }
            itemsIndexed(list, key = { _, c -> c.code }) { i, c ->
                if (q.isBlank() && i == 1) SnapSection("Countries", "${list.size - 1}")
                SnapMenuRow(
                    title = c.name,
                    subtitle = if (c.code == STREAMING.code) "Netflix, Prime Video, Pluto TV, Samsung TV Plus…" else null,
                    index = i, selected = c.code == s.country.code,
                    leading = { Text(c.flag, fontSize = 24.sp) },
                    trailing = { if (c.code == s.country.code) Icon(Icons.Filled.Check, null, tint = Snap.Accent) },
                ) {
                    if (c.code != s.country.code) vm.setCountry(c)
                    onPicked()
                }
            }
        }
    }
}
