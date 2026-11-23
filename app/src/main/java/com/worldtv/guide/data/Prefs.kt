package com.worldtv.guide.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

/** A followed show. [key] is "tm:<tvmaze id>" or "t:<normalised title>" (see [Programme.favKey]). */
data class FavShow(val key: String, val name: String, val image: String?)

/** Followed shows, source settings and other preferences, persisted in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("worldtv", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _favShows = MutableStateFlow(decodeShows(sp.getStringSet(KEY_SH, emptySet())!!))
    val favShows: StateFlow<List<FavShow>> = _favShows.asStateFlow()

    private val _disabled = MutableStateFlow(sp.getStringSet(KEY_DISABLED, emptySet())!!.toSet())
    /** Source ids the user switched off (everything is on by default). */
    val disabledSources: StateFlow<Set<String>> = _disabled.asStateFlow()

    private val _custom = MutableStateFlow(
        runCatching { json.decodeFromString<List<CustomSource>>(sp.getString(KEY_CUSTOM, "[]")!!) }.getOrDefault(emptyList()),
    )
    val customSources: StateFlow<List<CustomSource>> = _custom.asStateFlow()

    var themeId: String?
        get() = sp.getString(KEY_THEME, null)
        set(v) { sp.edit().putString(KEY_THEME, v).apply() }

    /** "list" or "grid". */
    var guideView: String
        get() = sp.getString(KEY_VIEW, "list") ?: "list"
        set(v) { sp.edit().putString(KEY_VIEW, v).apply() }

    var guideCountry: String
        get() = sp.getString(KEY_COUNTRY, null) ?: defaultCountry()
        set(v) { sp.edit().putString(KEY_COUNTRY, v).apply() }

    fun isFollowing(key: String) = _favShows.value.any { it.key == key }

    fun toggleShow(show: FavShow) {
        val list = _favShows.value.toMutableList()
        if (list.none { it.key == show.key }) list.add(show) else list.removeAll { it.key == show.key }
        list.sortBy { it.name.lowercase() }
        _favShows.value = list
        sp.edit().putStringSet(KEY_SH, list.map { "${it.key}\t${it.name}\t${it.image ?: ""}" }.toSet()).apply()
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        val s = _disabled.value.toMutableSet()
        if (enabled) s.remove(id) else s.add(id)
        _disabled.value = s
        sp.edit().putStringSet(KEY_DISABLED, s).apply()
    }

    fun addCustomSource(name: String, url: String, country: String?) {
        val list = _custom.value + CustomSource(UUID.randomUUID().toString().take(8), name.trim(), url.trim(), country)
        saveCustom(list)
    }

    fun removeCustomSource(id: String) {
        saveCustom(_custom.value.filterNot { it.id == id })
        setSourceEnabled("custom:$id", true)
    }

    private fun saveCustom(list: List<CustomSource>) {
        _custom.value = list
        sp.edit().putString(KEY_CUSTOM, json.encodeToString(list)).apply()
    }

    private fun decodeShows(set: Set<String>) = set.mapNotNull {
        val p = it.split('\t')
        val rawKey = p.getOrNull(0)?.takeIf { k -> k.isNotBlank() } ?: return@mapNotNull null
        // Version 1 stored a bare TVmaze id.
        val key = if (rawKey.all { c -> c.isDigit() }) "tm:$rawKey" else rawKey
        FavShow(key, p.getOrElse(1) { "" }, p.getOrNull(2)?.ifBlank { null })
    }.sortedBy { it.name.lowercase() }

    private fun defaultCountry(): String {
        val c = Locale.getDefault().country.uppercase()
        return when {
            c.isBlank() -> "US"
            c == "GB" -> "UK"
            else -> c
        }
    }

    companion object {
        private const val KEY_SH = "fav_shows"
        private const val KEY_COUNTRY = "guide_country"
        private const val KEY_THEME = "theme"
        private const val KEY_VIEW = "guide_view"
        private const val KEY_DISABLED = "disabled_sources"
        private const val KEY_CUSTOM = "custom_sources"
    }
}
