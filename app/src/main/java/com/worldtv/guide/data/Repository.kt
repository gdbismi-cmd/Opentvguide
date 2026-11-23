package com.worldtv.guide.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

class Directory(
    val countries: List<Country>,
    val categories: Map<String, String>,
    val channels: List<Channel>,
) {
    val countryByCode: Map<String, Country> = countries.associateBy { it.code }
    val channelsByCountry: Map<String, List<Channel>> = channels.groupBy { it.countryCode }
    val channelById: Map<String, Channel> = channels.associateBy { it.id }

    fun country(code: String): Country? =
        countryByCode[code] ?: if (code == "GB") countryByCode["UK"] else null
}

class TvRepository(cacheDir: File) {

    private val http = CachedHttp(File(cacheDir, "http").apply { mkdirs() })
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val dirMutex = Mutex()
    @Volatile private var directory: Directory? = null

    // ---------------- Worldwide channel directory (iptv-org) ----------------

    @Volatile private var countriesCache: List<Country>? = null

    /** Lightweight country list (no channels) – used by the TV guide picker. */
    suspend fun countries(): List<Country> {
        countriesCache?.let { return it }
        val raw = http.get("$IPTV/countries.json", "iptv_countries.json", 7L * 24 * 60 * 60 * 1000)
        return withContext(Dispatchers.Default) {
            json.decodeFromString<List<IptvCountry>>(raw).map { Country(it.code, it.name, it.flag) }.sortedBy { it.name }
        }.also { countriesCache = it }
    }

    suspend fun directory(force: Boolean = false): Directory = dirMutex.withLock {
        directory?.takeIf { !force }?.let { return it }
        val day = 24L * 60 * 60 * 1000
        coroutineScope {
            val chJob = async { http.get("$IPTV/channels.json", "iptv_channels.json", day, force) }
            val caJob = async { http.get("$IPTV/categories.json", "iptv_categories.json", 7 * day, force) }
            val loJob = async {
                runCatching { http.get("$IPTV/logos.json", "iptv_logos.json", day, force) }.getOrNull()
            }
            val countries = countries()
            val chRaw = chJob.await()
            val caRaw = caJob.await(); val loRaw = loJob.await()

            withContext(Dispatchers.Default) {
                val categories = json.decodeFromString<List<IptvCategory>>(caRaw)
                    .associate { it.id to it.name }
                val logos = HashMap<String, String>()
                loRaw?.let { raw ->
                    runCatching { json.decodeFromString<List<IptvLogo>>(raw) }.getOrNull()?.forEach { l ->
                        // Prefer the channel-level logo (no feed) over feed-specific ones.
                        if (l.feed == null || !logos.containsKey(l.channel)) logos[l.channel] = l.url
                    }
                }
                val channels = json.decodeFromString<List<IptvChannel>>(chRaw)
                    .asSequence()
                    .filter { !it.isNsfw && it.closed == null }
                    .map {
                        Channel(
                            id = it.id, name = it.name, altNames = it.altNames, network = it.network,
                            owners = it.owners, countryCode = it.country, city = it.city,
                            categories = it.categories.map { c -> categories[c] ?: c },
                            launched = it.launched, website = it.website,
                            logo = logos[it.id] ?: it.logo,
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                    .toList()
                Directory(countries, categories, channels)
            }
        }.also { directory = it }
    }

    // ---------------- TV schedules (TVmaze) ----------------

    private val episodeCache = HashMap<Long, TmEpisode>()

    /** All programmes airing on [date] in [country] (use [STREAMING] for streaming services). */
    suspend fun schedule(country: Country, date: LocalDate, force: Boolean = false): List<TmEpisode> {
        val ttl = 30L * 60 * 1000
        val raw = if (country.code == STREAMING.code) {
            http.get("$TVMAZE/schedule/web?date=$date", "tm_web_$date.json", ttl, force)
        } else {
            http.get(
                "$TVMAZE/schedule?country=${country.isoCode}&date=$date",
                "tm_${country.isoCode}_$date.json", ttl, force,
            )
        }
        val eps = withContext(Dispatchers.Default) { json.decodeFromString<List<TmEpisode>>(raw) }
        synchronized(episodeCache) { eps.forEach { episodeCache[it.id] = it } }
        return eps.distinctBy { it.id }.sortedBy { it.airstamp ?: "" }
    }

    suspend fun episode(id: Long): TmEpisode {
        synchronized(episodeCache) { episodeCache[id] }?.takeIf { it.theShow != null }?.let { return it }
        val raw = http.get("$TVMAZE/episodes/$id?embed=show", "tm_ep_$id.json", 6L * 60 * 60 * 1000)
        return json.decodeFromString<TmEpisode>(raw).also {
            synchronized(episodeCache) { episodeCache[id] = it }
        }
    }

    companion object {
        const val IPTV = "https://iptv-org.github.io/api"
        const val TVMAZE = "https://api.tvmaze.com"
    }
}
