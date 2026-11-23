package com.worldtv.guide

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.worldtv.guide.data.Programme
import com.worldtv.guide.ui.components.SoftKeyBar
import com.worldtv.guide.ui.country.CountryScreen
import com.worldtv.guide.ui.detail.EpisodeDetailScreen
import com.worldtv.guide.ui.detail.ProgrammeDetailScreen
import com.worldtv.guide.ui.favorites.FavoritesScreen
import com.worldtv.guide.ui.guide.GuideMode
import com.worldtv.guide.ui.guide.GuideScreen
import com.worldtv.guide.ui.guide.GuideViewModel
import com.worldtv.guide.ui.home.HomeScreen
import com.worldtv.guide.ui.options.OptionsScreen
import com.worldtv.guide.ui.options.ThemesScreen
import com.worldtv.guide.ui.sources.SourcesScreen
import com.worldtv.guide.ui.theme.Snap
import com.worldtv.guide.ui.theme.WorldTvTheme

class MainActivity : ComponentActivity() {

    private val guideVm: GuideViewModel by viewModels { GuideViewModel.factory(application as WorldTvApp) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Title and soft-key bars are dark in every skin, so system bar icons stay light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as WorldTvApp
        setContent {
            WorldTvTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route.orEmpty()
                val atHome = route == "home"

                fun goHome() { nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false) }
                fun openGuide(mode: GuideMode) = nav.navigate("guide/${mode.name}") { launchSingleTop = true }
                fun openProgramme(p: Programme) {
                    val ep = p.tvmazeEpisodeId
                    if (ep != null) nav.navigate("episode/$ep") else nav.navigate("programme/${Uri.encode(p.id)}")
                }

                Column(Modifier.fillMaxSize().background(Snap.Canvas).windowInsetsPadding(WindowInsets.ime)) {
                    Box(Modifier.weight(1f)) {
                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                HomeScreen(
                                    guideVm, app.prefs,
                                    onGuide = ::openGuide,
                                    onFollowing = { nav.navigate("following") },
                                    onCountry = { nav.navigate("country") },
                                    onSources = { nav.navigate("sources") },
                                    onThemes = { nav.navigate("themes") },
                                )
                            }
                            composable("options") {
                                OptionsScreen(
                                    guideVm,
                                    onThemes = { nav.navigate("themes") },
                                    onSources = { nav.navigate("sources") },
                                    onCountry = { nav.navigate("country") },
                                )
                            }
                            composable("themes") { ThemesScreen(app.prefs) }
                            composable(
                                "guide/{mode}",
                                arguments = listOf(navArgument("mode") { type = NavType.StringType }),
                            ) { entry ->
                                val mode = runCatching { GuideMode.valueOf(entry.arguments?.getString("mode").orEmpty()) }
                                    .getOrDefault(GuideMode.ALL)
                                GuideScreen(
                                    guideVm, app.prefs, mode,
                                    onProgramme = ::openProgramme,
                                    onSources = { nav.navigate("sources") },
                                    onCountry = { nav.navigate("country") },
                                )
                            }
                            composable("following") {
                                FavoritesScreen(
                                    guideVm, app.prefs,
                                    onProgramme = ::openProgramme,
                                    onShowInGuide = { name -> guideVm.setQuery(name); openGuide(GuideMode.SEARCH) },
                                )
                            }
                            composable("country") { CountryScreen(guideVm, onPicked = { nav.popBackStack() }) }
                            composable("sources") { SourcesScreen(guideVm, app.prefs) }
                            composable(
                                "programme/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                            ) { entry ->
                                ProgrammeDetailScreen(Uri.decode(entry.arguments?.getString("id").orEmpty()), app.repo, app.prefs)
                            }
                            composable(
                                "episode/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                            ) { entry ->
                                EpisodeDetailScreen(entry.arguments?.getLong("id") ?: 0L, app.repo, app.prefs)
                            }
                        }
                    }
                    SoftKeyBar(
                        left = if (atHome) "Options" else "Home",
                        onLeft = { if (atHome) nav.navigate("options") else goHome() },
                        right = if (atHome) "Exit" else "Back",
                        onRight = { if (atHome) finish() else nav.popBackStack() },
                        center = if (atHome || route.startsWith("guide")) Icons.Filled.Refresh else Icons.Filled.Home,
                        onCenter = { if (atHome || route.startsWith("guide")) guideVm.load(force = true) else goHome() },
                    )
                }
            }
        }
    }
}
