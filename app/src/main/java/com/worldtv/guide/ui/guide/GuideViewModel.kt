package com.worldtv.guide.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.worldtv.guide.WorldTvApp
import com.worldtv.guide.data.Country
import com.worldtv.guide.data.FeedResult
import com.worldtv.guide.data.FeedStatus
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.Programme
import com.worldtv.guide.data.STREAMING
import com.worldtv.guide.data.SourceIds
import com.worldtv.guide.data.TvRepository
import com.worldtv.guide.data.normalizeTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class GuideState(
    val countries: List<Country> = emptyList(),
    val country: Country = Country("US", "United States", "🇺🇸"),
    val date: LocalDate = LocalDate.now(),
    val feeds: List<FeedResult> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val query: String = "",
    val onNowOnly: Boolean = false,
    val hideEnded: Boolean = true,
    val byChannel: Boolean = true,
    val grid: Boolean = false,
    val genre: String? = null,
) {
    val loading: Boolean get() = feeds.isEmpty() || feeds.any { it.status == FeedStatus.LOADING }
    val allFailed: Boolean get() = feeds.isNotEmpty() && feeds.all { it.status == FeedStatus.FAILED }
}

class GuideViewModel(private val repo: TvRepository, private val prefs: Prefs) : ViewModel() {

    private val _state = MutableStateFlow(GuideState(grid = prefs.guideView == "grid"))
    val state: StateFlow<GuideState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = prefs.guideCountry
            val list = runCatching { repo.countries() }.getOrDefault(emptyList())
            val selected = if (saved == STREAMING.code) STREAMING
            else list.firstOrNull { it.code == saved } ?: list.firstOrNull { it.code == "US" } ?: _state.value.country
            _state.update { it.copy(countries = listOf(STREAMING) + list, country = selected) }
            load()
        }
        // Reload when sources are switched on/off or custom guides change.
        viewModelScope.launch { prefs.disabledSources.drop(1).collect { load() } }
        viewModelScope.launch { prefs.customSources.drop(1).collect { load() } }
    }

    fun load(force: Boolean = false) {
        loadJob?.cancel()
        val s = _state.value
        _state.update { it.copy(feeds = emptyList(), programmes = emptyList()) }
        loadJob = viewModelScope.launch {
            val results = LinkedHashMap<String, FeedResult>()
            repo.guide(s.country, s.date, prefs.disabledSources.value, prefs.customSources.value, force)
                .collect { r ->
                    results[r.key] = r
                    val feeds = results.values.toList()
                    val merged = if (r.status == FeedStatus.LOADING) null
                    else withContext(Dispatchers.Default) { merge(feeds) }
                    _state.update { st ->
                        if (merged == null) st.copy(feeds = feeds) else st.copy(feeds = feeds, programmes = merged)
                    }
                }
            // A source lookup that finished without any feeds should not count as "loading".
            if (results.isEmpty()) _state.update { it.copy(feeds = listOf(noSources())) }
        }
    }

    private fun noSources() = FeedResult("none", "none", "No sources", "No sources", FeedStatus.FAILED,
        error = "None of your enabled sources cover this country")

    /**
     * Combines every finished feed, dropping duplicates of the same airing on the same channel
     * (TVmaze wins because it has artwork and synopses).
     */
    private fun merge(feeds: List<FeedResult>): List<Programme> {
        val seen = HashSet<String>()
        val out = ArrayList<Programme>()
        feeds.filter { it.status == FeedStatus.DONE }
            .sortedBy { if (it.sourceId == SourceIds.TVMAZE) 0 else 1 }
            .forEach { f ->
                f.programmes.forEach { p ->
                    val key = "${p.channelKey}|${p.start.epochSecond / 300}|${normalizeTitle(p.title)}"
                    if (seen.add(key)) out += p
                }
            }
        out.sortBy { it.start }
        return out
    }

    fun setCountry(c: Country) {
        prefs.guideCountry = c.code
        _state.update { it.copy(country = c, genre = null) }
        load()
    }

    fun setDate(d: LocalDate) {
        val today = LocalDate.now()
        _state.update { it.copy(date = d, hideEnded = d == today && it.hideEnded, onNowOnly = d == today && it.onNowOnly) }
        load()
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun toggleOnNow() = _state.update { it.copy(onNowOnly = !it.onNowOnly) }
    fun toggleHideEnded() = _state.update { it.copy(hideEnded = !it.hideEnded) }
    fun toggleGrouping() = _state.update { it.copy(byChannel = !it.byChannel) }
    fun setGrid(grid: Boolean) {
        prefs.guideView = if (grid) "grid" else "list"
        _state.update { it.copy(grid = grid) }
    }
    fun setGenre(g: String?) = _state.update { it.copy(genre = g) }

    /** Jump here from a channel page: switch to that channel's country and search for it. */
    fun openFromChannel(countryCode: String, channelName: String) {
        val c = _state.value.countries.firstOrNull { it.code == countryCode }
        _state.update { it.copy(query = channelName, onNowOnly = false, genre = null) }
        if (_state.value.date != LocalDate.now()) _state.update { it.copy(date = LocalDate.now()) }
        if (c != null && c.code != _state.value.country.code) setCountry(c) else load()
    }

    companion object {
        fun factory(app: WorldTvApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { GuideViewModel(app.repo, app.prefs) }
        }
    }
}
