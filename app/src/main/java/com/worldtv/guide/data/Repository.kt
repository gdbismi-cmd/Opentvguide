package com.worldtv.guide.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class TvRepository(cacheDir: File) {

    private val http = CachedHttp(File(cacheDir, "http").apply { mkdirs() })
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }


    // ---------------- Countries (iptv-org) ----------------

    @Volatile private var countriesCache: List<Country>? = null

    /** Lightweight country list (no channels) – used by the TV guide picker. */
    suspend fun countries(): List<Country> {
        countriesCache?.let { return it }
        val raw = http.get("$IPTV/countries.json", "iptv_countries.json", 7L * 24 * 60 * 60 * 1000)
        return withContext(Dispatchers.Default) {
            json.decodeFromString<List<IptvCountry>>(raw).map { Country(it.code, it.name, it.flag) }.sortedBy { it.name }
        }.also { countriesCache = it }
    }

    // ---------------- Channel logos (iptv-org) ----------------

    private val logoMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var logoIndex: LogoIndex? = null

    /** Name → logo lookup, downloaded once a week and kept in memory. Null if it can't be loaded. */
    suspend fun logos(): LogoIndex? {
        logoIndex?.let { return it }
        return logoMutex.withLock {
            logoIndex ?: runCatching {
                val week = 7L * 24 * 60 * 60 * 1000
                val ch = http.get("$IPTV/channels.json", "iptv_channels.json", week)
                val lo = http.get("$IPTV/logos.json", "iptv_logos.json", week)
                val fe = runCatching { http.get("$IPTV/feeds.json", "iptv_feeds.json", week) }.getOrNull()
                val la = runCatching { http.get("$IPTV/languages.json", "iptv_languages.json", 30 * week) }.getOrNull()
                withContext(Dispatchers.Default) {
                    LogoIndex.build(
                        json.decodeFromString<List<IptvLogoChannel>>(ch),
                        json.decodeFromString<List<IptvLogo>>(lo),
                        fe?.let { runCatching { json.decodeFromString<List<IptvFeed>>(it) }.getOrNull() }.orEmpty(),
                        la?.let { runCatching { json.decodeFromString<List<IptvLanguage>>(it) }.getOrNull() }.orEmpty(),
                    )
                }
            }.getOrNull()?.also { logoIndex = it }
        }
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
    private val parseLimit = Semaphore(3) // bound memory: at most three big XML files parsed at once
    private val parsedCache = object : LinkedHashMap<String, XmltvResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, XmltvResult>?) = size > 10
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

        // Each source is looked up on its own, and its guide files start loading as soon as it answers.
        val started = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        fun start(feeds: List<Feed>) = feeds.forEach { f ->
            if (started.add(f.url)) launch { loadFeed(f, date, from, to, force) }
        }
        launch { start(resolve(SourceIds.EPGSHARE, "epgshare01") { resolver.epgshare(country) }) }
        launch { start(resolve(SourceIds.EPGPW, "epg.pw") { resolver.epgPw(country) }) }
        launch { start(resolve(SourceIds.GLOBETV, "GlobeTV") { resolver.globeTv(country) }) }
        launch { start(resolve(SourceIds.FAST, "Free streaming TV") { resolver.fast(country) }) }
        start(resolver.custom(country, custom).filter { it.sourceId !in disabled })
    }

    private suspend fun ProducerScope<FeedResult>.loadFeed(
        f: Feed, date: LocalDate, from: java.time.Instant, to: java.time.Instant, force: Boolean,
    ) {
        val base = FeedResult(f.url, f.sourceId, f.sourceName, f.label, FeedStatus.LOADING)
        send(base)
        val cacheKey = "${f.url}|$date"
        runCatching {
            synchronized(parsedCache) { parsedCache[cacheKey] }?.takeIf { !force }?.let { return@runCatching it }
            val file = http.getFile(f.url, maxAgeMs = 6L * 60 * 60 * 1000, force = force, fallbacks = f.fallbacks)
            parseLimit.withPermit {
                withContext(Dispatchers.Default) {
                    XmltvParser.parseFile(file, from, to, f.sourceId, "${f.sourceName} · ${f.label}")
                }
            }.also { result ->
                synchronized(parsedCache) { parsedCache[cacheKey] = result }
                if (programmeIndex.size > 300_000) programmeIndex.clear()
                result.programmes.forEach { programmeIndex[it.id] = it }
            }
        }
            .onSuccess { send(base.copy(status = FeedStatus.DONE, programmes = it.programmes, coversUntil = it.coversUntil)) }
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
    /** For guide files: the last time the file has listings for (null if unknown). */
    val coversUntil: java.time.Instant? = null,
) {
    /** Loaded fine but has nothing for the chosen day, usually because the file is out of date. */
    val emptyForDay: Boolean get() = status == FeedStatus.DONE && programmes.isEmpty()

    val staleNote: String?
        get() = if (!emptyForDay) null else coversUntil?.let {
            "guide ends " + java.time.format.DateTimeFormatter.ofPattern("d MMM").format(it.atZone(ZoneId.systemDefault()))
        } ?: "no listings for this day"
}
