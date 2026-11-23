package com.worldtv.guide.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.worldtv.guide.WorldTvApp
import com.worldtv.guide.data.Country
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.STREAMING
import com.worldtv.guide.data.TmEpisode
import com.worldtv.guide.data.TvRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GuideState(
    val countries: List<Country> = emptyList(),
    val country: Country = Country("US", "United States", "🇺🇸"),
    val date: LocalDate = LocalDate.now(),
    val episodes: List<TmEpisode> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val onNowOnly: Boolean = false,
    val hideEnded: Boolean = true,
    val byChannel: Boolean = true,
    val genre: String? = null,
)

class GuideViewModel(private val repo: TvRepository, private val prefs: Prefs) : ViewModel() {

    private val _state = MutableStateFlow(GuideState())
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
    }

    fun load(force: Boolean = false) {
        loadJob?.cancel()
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { repo.schedule(s.country, s.date, force) }
                .onSuccess { eps -> _state.update { it.copy(episodes = eps, loading = false) } }
                .onFailure { e ->
                    _state.update { it.copy(episodes = emptyList(), loading = false,
                        error = e.message ?: "Couldn't load the schedule") }
                }
        }
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
    fun setGenre(g: String?) = _state.update { it.copy(genre = g) }

    /** Jump here from a channel page: switch to that channel's country and search for it. */
    fun openFromChannel(countryCode: String, channelName: String) {
        val c = _state.value.countries.firstOrNull { it.code == countryCode }
        _state.update { it.copy(query = channelName, onNowOnly = false, genre = null, date = LocalDate.now()) }
        if (c != null && c.code != _state.value.country.code) setCountry(c) else load()
    }

    companion object {
        fun factory(app: WorldTvApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { GuideViewModel(app.repo, app.prefs) }
        }
    }
}
