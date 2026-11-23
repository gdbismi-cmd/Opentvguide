package com.worldtv.guide.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object SourceIds {
    const val TVMAZE = "tvmaze"
    const val EPGSHARE = "epgshare01"
    const val GLOBETV = "globetv"
    const val FAST = "fast"
}

data class SourceInfo(val id: String, val name: String, val description: String, val url: String)

val BUILT_IN_SOURCES = listOf(
    SourceInfo(SourceIds.TVMAZE, "TVmaze",
        "Show-level listings with artwork and synopses. Strongest for the US, UK, Canada, Australia, Europe and Japan, plus streaming services.",
        "https://www.tvmaze.com"),
    SourceInfo(SourceIds.EPGSHARE, "epgshare01",
        "Full channel-by-channel XMLTV guides for about 70 countries (incl. India, Middle East, Latin America, Asia), rebuilt daily.",
        "https://epgshare01.online"),
    SourceInfo(SourceIds.GLOBETV, "GlobeTV EPG",
        "Per-country XMLTV guides hosted on GitHub, updated daily.",
        "https://github.com/globetvapp/epg"),
    SourceInfo(SourceIds.FAST, "Free streaming TV",
        "Pluto TV, Samsung TV Plus, Plex and Roku channel guides (shown under 🌐 Streaming). Large downloads.",
        "https://i.mjh.nz"),
)

/** A guide the user added. [country] = iptv-org country code, "WEB" for streaming, or null for every country. */
@Serializable
data class CustomSource(val id: String, val name: String, val url: String, val country: String? = null)

/** One downloadable guide file for the current country. */
data class Feed(val sourceId: String, val sourceName: String, val label: String, val url: String)

/** Works out which guide files exist for a country, from each source's public listings. */
class FeedResolver(private val http: CachedHttp, private val json: Json) {

    private val day = 24L * 60 * 60 * 1000

    suspend fun epgshare(country: Country): List<Feed> {
        val names = runCatching {
            val html = http.get("https://epgshare01.online/epgshare01/", "epgshare_index.html", day)
            Regex("epg_ripper_([A-Za-z0-9_\\-.]+?)\\.xml\\.gz").findAll(html).map { it.groupValues[1] }.toSortedSet()
        }.getOrNull()

        val picks: List<String> = if (country.code == STREAMING.code) {
            val wanted = listOf("PLEX1", "DISTROTV1", "PEACOCK1", "AMCCHANNELS1", "WHALETVPLUS1")
            if (names == null) wanted else wanted.filter { it in names }
        } else {
            // epgshare01 uses "UK" (like iptv-org) rather than ISO "GB".
            val cc = if (country.code == "GB") "UK" else country.code
            val pattern = Regex("^${Regex.escape(cc)}(_SPORTS)?\\d+$")
            names?.filter { pattern.matches(it) } ?: listOf("${cc}1")
        }
        return picks.map {
            Feed(SourceIds.EPGSHARE, "epgshare01", it, "https://epgshare01.online/epgshare01/epg_ripper_$it.xml.gz")
        }
    }

    suspend fun globeTv(country: Country): List<Feed> {
        if (country.code == STREAMING.code) return emptyList()
        val api = "https://api.github.com/repos/globetvapp/epg/contents"
        val root = json.parseToJsonElement(http.get(api, "globetv_root.json", day)) as JsonArray
        val target = setOf(norm(country.name), norm(country.code)) + aliases(country.code)
        val dir = root.map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "dir" }
            .map { it["name"]!!.jsonPrimitive.content }
            .firstOrNull { norm(it) in target } ?: return emptyList()
        val listing = json.parseToJsonElement(
            http.get("$api/${dir.replace(" ", "%20")}", "globetv_dir_$dir.json", day),
        ) as JsonArray
        return listing.map { it.jsonObject }
            .filter { o ->
                val n = o["name"]?.jsonPrimitive?.content.orEmpty()
                o["type"]?.jsonPrimitive?.content == "file" && (n.endsWith(".xml") || n.endsWith(".xml.gz"))
            }
            .sortedBy { it["name"]!!.jsonPrimitive.content }
            .take(4)
            .mapNotNull { o ->
                val url = o["download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                Feed(SourceIds.GLOBETV, "GlobeTV", o["name"]!!.jsonPrimitive.content.substringBefore('.'), url)
            }
    }

    fun fast(country: Country): List<Feed> {
        if (country.code != STREAMING.code) return emptyList()
        val base = "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master"
        return listOf("PlutoTV" to "Pluto TV", "SamsungTVPlus" to "Samsung TV Plus", "Plex" to "Plex", "Roku" to "Roku")
            .map { (dir, label) -> Feed(SourceIds.FAST, "Free streaming TV", label, "$base/$dir/all.xml.gz") }
    }

    fun custom(country: Country, sources: List<CustomSource>): List<Feed> =
        sources.filter { it.country == null || it.country == country.code }
            .map { Feed("custom:${it.id}", it.name, it.name, it.url) }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun aliases(code: String): Set<String> = when (code) {
        "UK", "GB" -> setOf("uk", "unitedkingdom", "greatbritain", "england")
        "US" -> setOf("usa", "us", "unitedstates", "unitedstatesofamerica")
        "AE" -> setOf("uae", "unitedarabemirates")
        "KR" -> setOf("southkorea", "korea")
        "CZ" -> setOf("czechia", "czechrepublic")
        "NL" -> setOf("netherlands", "holland")
        "RU" -> setOf("russia", "russianfederation")
        "TR" -> setOf("turkey", "turkiye")
        else -> emptySet()
    }
}
