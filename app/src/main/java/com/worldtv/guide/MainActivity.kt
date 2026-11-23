package com.worldtv.guide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.worldtv.guide.ui.channels.ChannelDetailScreen
import com.worldtv.guide.ui.channels.ChannelsScreen
import com.worldtv.guide.ui.channels.ChannelsViewModel
import com.worldtv.guide.ui.detail.EpisodeDetailScreen
import com.worldtv.guide.ui.favorites.FavoritesScreen
import com.worldtv.guide.ui.guide.GuideScreen
import com.worldtv.guide.ui.guide.GuideViewModel
import com.worldtv.guide.ui.theme.WorldTvTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("guide", "TV Guide", Icons.Filled.LiveTv),
    Tab("channels", "Channels", Icons.Filled.Public),
    Tab("favorites", "Favourites", Icons.Filled.Favorite),
)

class MainActivity : ComponentActivity() {

    private val guideVm: GuideViewModel by viewModels { GuideViewModel.factory(application as WorldTvApp) }
    private val channelsVm: ChannelsViewModel by viewModels { ChannelsViewModel.factory(application as WorldTvApp) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WorldTvApp
        setContent {
            WorldTvTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                fun goTab(route: String) = nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }

                Scaffold(
                    bottomBar = {
                        if (tabs.any { it.route == current }) {
                            NavigationBar {
                                tabs.forEach { t ->
                                    NavigationBarItem(
                                        selected = current == t.route,
                                        onClick = { goTab(t.route) },
                                        icon = { Icon(t.icon, null) },
                                        label = { Text(t.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    NavHost(nav, startDestination = "guide", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
                        composable("guide") {
                            GuideScreen(guideVm, app.prefs, onEpisode = { nav.navigate("episode/$it") })
                        }
                        composable("channels") {
                            ChannelsScreen(channelsVm, app.prefs, onChannel = { nav.navigate("channel/$it") })
                        }
                        composable("favorites") {
                            FavoritesScreen(
                                guideVm, channelsVm, app.prefs,
                                onChannel = { nav.navigate("channel/$it") },
                                onEpisode = { nav.navigate("episode/$it") },
                                onShowInGuide = { name -> guideVm.setQuery(name); goTab("guide") },
                            )
                        }
                        composable(
                            "channel/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            ChannelDetailScreen(
                                id = entry.arguments?.getString("id").orEmpty(),
                                vm = channelsVm, prefs = app.prefs,
                                onBack = { nav.popBackStack() },
                                onFindInGuide = { countryCode, name ->
                                    guideVm.openFromChannel(countryCode, name)
                                    goTab("guide")
                                },
                            )
                        }
                        composable(
                            "episode/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { entry ->
                            EpisodeDetailScreen(
                                id = entry.arguments?.getLong("id") ?: 0L,
                                repo = app.repo, prefs = app.prefs,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
