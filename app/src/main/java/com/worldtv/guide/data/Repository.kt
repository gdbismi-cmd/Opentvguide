package com.worldtv.guide.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

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

    // ---------------- All guide sources, merged ----------------

    private val resolver = FeedResolver(http, json)
    private val parseLimit = Semaphore(2) // bound memory: at most two big XML files parsed at once
    private val parsedCache = object : LinkedHashMap<String, List<Programme>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Programme>>?) = size > 10
    }
    private val programmeIndex = java.util.concurrent.ConcurrentHashMap<String, Programme>()

    fun programme(id: String): Programme? = programmeIndex[id]

    /**
     * Loads every enabled source for [country] on [date] in parallel and emits each
     * feed's status as it changes (loading → done/failed).
     */
    fun guide(
        country: Country, date: LocalDate, disabled: Set<String>,
        custom: List<CustomSource>, force: Boolean = false,
    ): Flow<FeedResult> = channelFlow {
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()

        if (SourceIds.TVMAZE !in disabled) launch {
            val base = FeedResult("tvmaze", SourceIds.TVMAZE, "TVmaze", "TVmaze", FeedStatus.LOADING)
            send(base)
            runCatching { schedule(country, date, force).mapNotNull { it.toProgramme() } }
                .onSuccess { send(base.copy(status = FeedStatus.DONE, programmes = it)) }
                .onFailure { send(base.copy(status = FeedStatus.FAILED, error = it.message)) }
        }

        suspend fun resolve(id: String, name: String, block: suspend () -> List<Feed>): List<Feed> =
            if (id in disabled) emptyList()
            else runCatching { block() }.getOrElse { e ->
                send(FeedResult("resolve:$id", id, name, name, FeedStatus.FAILED, error = e.message ?: "Lookup failed"))
                emptyList()
            }

        launch {
            val feeds = resolve(SourceIds.EPGSHARE, "epgshare01") { resolver.epgshare(country) } +
                resolve(SourceIds.GLOBETV, "GlobeTV") { resolver.globeTv(country) } +
                resolve(SourceIds.FAST, "Free streaming TV") { resolver.fast(country) } +
                resolver.custom(country, custom).filter { it.sourceId !in disabled }
            feeds.distinctBy { it.url }.forEach { f -> launch { loadFeed(f, date, from, to, force) } }
        }
    }

    private suspend fun ProducerScope<FeedResult>.loadFeed(
        f: Feed, date: LocalDate, from: java.time.Instant, to: java.time.Instant, force: Boolean,
    ) {
        val base = FeedResult(f.url, f.sourceId, f.sourceName, f.label, FeedStatus.LOADING)
        send(base)
        val cacheKey = "${f.url}|$date"
        runCatching {
            synchronized(parsedCache) { parsedCache[cacheKey] }?.takeIf { !force }?.let { return@runCatching it }
            val file = http.getFile(f.url, maxAgeMs = 6L * 60 * 60 * 1000, force = force)
            parseLimit.withPermit {
                withContext(Dispatchers.Default) {
                    XmltvParser.parseFile(file, from, to, f.sourceId, "${f.sourceName} · ${f.label}")
                }
            }.also { list ->
                synchronized(parsedCache) { parsedCache[cacheKey] = list }
                if (programmeIndex.size > 300_000) programmeIndex.clear()
                list.forEach { programmeIndex[it.id] = it }
            }
        }
            .onSuccess { send(base.copy(status = FeedStatus.DONE, programmes = it)) }
            .onFailure { send(base.copy(status = FeedStatus.FAILED, error = it.message ?: it.javaClass.simpleName)) }
    }

    companion object {
        const val IPTV = "https://iptv-org.github.io/api"
        const val TVMAZE = "https://api.tvmaze.com"
    }
}

enum class FeedStatus { LOADING, DONE, FAILED }

data class FeedResult(
    val key: String,
    val sourceId: String,
    val sourceName: String,
    val label: String,
    val status: FeedStatus,
    val programmes: List<Programme> = emptyList(),
    val error: String? = null,
)
