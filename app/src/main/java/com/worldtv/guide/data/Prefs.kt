package com.worldtv.guide.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class FavShow(val id: Int, val name: String, val image: String?)

/** Favourites and settings, persisted in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("worldtv", Context.MODE_PRIVATE)

    private val _favChannels = MutableStateFlow(sp.getStringSet(KEY_CH, emptySet())!!.toSet())
    val favChannels: StateFlow<Set<String>> = _favChannels.asStateFlow()

    private val _favShows = MutableStateFlow(decodeShows(sp.getStringSet(KEY_SH, emptySet())!!))
    val favShows: StateFlow<List<FavShow>> = _favShows.asStateFlow()

    var guideCountry: String
        get() = sp.getString(KEY_COUNTRY, null) ?: defaultCountry()
        set(v) { sp.edit().putString(KEY_COUNTRY, v).apply() }

    var channelsCountry: String?
        get() = sp.getString(KEY_CH_COUNTRY, null)
        set(v) { sp.edit().putString(KEY_CH_COUNTRY, v).apply() }

    fun toggleChannel(id: String) {
        val s = _favChannels.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        _favChannels.value = s
        sp.edit().putStringSet(KEY_CH, s).apply()
    }

    fun toggleShow(show: FavShow) {
        val list = _favShows.value.toMutableList()
        if (list.none { it.id == show.id }) list.add(show) else list.removeAll { it.id == show.id }
        _favShows.value = list
        sp.edit().putStringSet(KEY_SH, list.map { "${it.id}\t${it.name}\t${it.image ?: ""}" }.toSet()).apply()
    }

    private fun decodeShows(set: Set<String>) = set.mapNotNull {
        val p = it.split('\t')
        p.getOrNull(0)?.toIntOrNull()?.let { id -> FavShow(id, p.getOrElse(1) { "" }, p.getOrNull(2)?.ifBlank { null }) }
    }.sortedBy { it.name }

    private fun defaultCountry(): String {
        val c = Locale.getDefault().country.uppercase()
        return when {
            c.isBlank() -> "US"
            c == "GB" -> "UK"
            else -> c
        }
    }

    companion object {
        private const val KEY_CH = "fav_channels"
        private const val KEY_SH = "fav_shows"
        private const val KEY_COUNTRY = "guide_country"
        private const val KEY_CH_COUNTRY = "channels_country"
    }
}
