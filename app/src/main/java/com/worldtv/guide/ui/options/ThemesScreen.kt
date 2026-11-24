package com.worldtv.guide.ui.options

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.ui.components.SnapSection
import com.worldtv.guide.ui.components.SnapTopBar
import com.worldtv.guide.ui.theme.Palette
import com.worldtv.guide.ui.theme.Palettes
import com.worldtv.guide.ui.theme.Snap

/** Skin picker. Each card is a miniature of the app drawn in that skin's colours. */
@Composable
fun ThemesScreen(prefs: Prefs) {
    val current = Snap.palette
    Column(Modifier.fillMaxSize().background(Snap.Canvas)) {
        SnapTopBar("Themes", subtitle = current.name, badge = "🎨")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            val (windows, snaptu) = Palettes.all.partition { it.id.startsWith("win") }
            listOf("Snaptu" to snaptu, "Windows" to windows).forEach { (title, group) ->
                item(key = "h_$title") { SnapSection(title, "${group.size}") }
                items(group, key = { it.id }) { p ->
                    ThemeCard(p, selected = p.id == current.id) {
                        Snap.palette = p
                        prefs.themeId = p.id
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(p: Palette, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Snap.Selected else Snap.Row)
            .border(2.dp, if (selected) Snap.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPhone(p)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Snap.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(p.description.ifBlank { if (p.light) "Light lists, blue title bar" else "Dark lists, glossy tiles" },
                color = Snap.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(p.barTop, p.accent, p.row, p.canvas).forEach { c ->
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(c)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp)))
                }
            }
        }
        if (selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = Snap.Accent)
    }
}

/** Tiny phone-screen preview: title bar, two tile rows, soft-key bar. */
@Composable
private fun MiniPhone(p: Palette) {
    val tileColors = listOf(
        Color(0xFFE53935), Color(0xFF5E35B1), Color(0xFF1E88E5),
        Color(0xFFFB8C00), Color(0xFF43A047), Color(0xFF00897B),
    )
    Column(
        Modifier.size(width = 64.dp, height = 104.dp).clip(RoundedCornerShape(8.dp)).background(p.canvas)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(p.barTop, p.barBottom))))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.verticalGradient(listOf(p.accent, p.accentDeep))))
        Column(Modifier.weight(1f).padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            tileColors.chunked(3).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    rowColors.forEach { c ->
                        Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp))
                            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.75f), c))))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(p.row))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(p.rowAlt))
        }
        Box(Modifier.fillMaxWidth().height(12.dp).background(Brush.verticalGradient(listOf(p.keyTop, p.keyBottom))),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 16.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(
                Brush.verticalGradient(listOf(p.centerTop ?: p.accent, p.centerBottom ?: p.accentDeep))))
        }
    }
}
