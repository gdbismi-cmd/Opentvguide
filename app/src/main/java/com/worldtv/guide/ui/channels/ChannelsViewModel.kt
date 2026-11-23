package com.worldtv.guide.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.worldtv.guide.WorldTvApp
import com.worldtv.guide.data.Channel
import com.worldtv.guide.data.Directory
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.TvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChannelsState(
    val directory: Directory? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val countryCode: String? = null, // null = all countries
    val query: String = "",
    val category: String? = null,
    val results: List<Channel> = emptyList(),
    val categoriesHere: List<String> = emptyList(),
)

class ChannelsViewModel(private val repo: TvRepository, private val prefs: Prefs) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsState(countryCode = prefs.channelsCountry ?: prefs.guideCountry))
    val state: StateFlow<ChannelsState> = _state.asStateFlow()
    private var filterJob: Job? = null

    init { load() }

    fun load(force: Boolean = false) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.directory(force) }
                .onSuccess { dir ->
                    _state.update {
                        val code = it.countryCode?.takeIf { c -> dir.countryByCode.containsKey(c) }
                        it.copy(directory = dir, loading = false, countryCode = code)
                    }
                    refilter(immediate = true)
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load channels") } }
        }
    }

    fun setCountry(code: String?) {
        prefs.channelsCountry = code
        _state.update { it.copy(countryCode = code, category = null) }
        refilter(immediate = true)
    }

    fun setQuery(q: String) { _state.update { it.copy(query = q) }; refilter() }
    fun setCategory(c: String?) { _state.update { it.copy(category = c) }; refilter(immediate = true) }

    private fun refilter(immediate: Boolean = false) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (!immediate) delay(200) // debounce typing
            val s = _state.value
            val dir = s.directory ?: return@launch
            val (results, cats) = withContext(Dispatchers.Default) {
                val base = s.countryCode?.let { dir.channelsByCountry[it].orEmpty() } ?: dir.channels
                val cats = base.flatMap { it.categories }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key }
                val q = s.query.trim()
                val cat = s.category
                val res = base.filter { ch ->
                    (cat == null || cat in ch.categories) &&
                        (q.isEmpty() || ch.name.contains(q, true) || ch.altNames.any { it.contains(q, true) } ||
                            ch.network?.contains(q, true) == true || ch.owners.any { it.contains(q, true) })
                }
                res to cats
            }
            _state.update { it.copy(results = results, categoriesHere = cats) }
        }
    }

    companion object {
        fun factory(app: WorldTvApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChannelsViewModel(app.repo, app.prefs) }
        }
    }
}
