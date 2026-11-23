package com.worldtv.guide.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Amber = Color(0xFFFFB347)
val Sky = Color(0xFF7FD1FF)
val LiveRed = Color(0xFFFF5A5F)

private val Dark = darkColorScheme(
    primary = Amber, onPrimary = Color(0xFF2B1A00),
    secondary = Sky, onSecondary = Color(0xFF00283A),
    background = Color(0xFF101326), onBackground = Color(0xFFE6E7F2),
    surface = Color(0xFF151935), onSurface = Color(0xFFE6E7F2),
    surfaceVariant = Color(0xFF232849), onSurfaceVariant = Color(0xFFB9BCD6),
    surfaceContainer = Color(0xFF1B1F3B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A5100), secondary = Color(0xFF00658C),
)

@Composable
fun WorldTvTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && !dark -> dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
