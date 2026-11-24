package com.worldtv.guide.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IptvLogoChannel(
    val id: String,
    val name: String,
    @SerialName("alt_names") val altNames: List<String> = emptyList(),
    val country: String = "",
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
)

@Serializable
data class IptvLogo(val channel: String, val feed: String? = null, val url: String)

/**
 * Finds a logo for a channel by its name, using the iptv-org database. Used when a guide
 * (TVmaze, or an XMLTV file without icons) doesn't supply one.
 */
class LogoIndex(private val byName: Map<String, List<Pair<String, String>>>) {

    /** [countryCode] is an iptv-org code ("UK" for the United Kingdom). */
    fun find(channelName: String, countryCode: String?): String? {
        for (key in candidates(channelName)) {
            val hits = byName[key] ?: continue
            return hits.firstOrNull { it.first == countryCode }?.second ?: hits.first().second
        }
        return null
    }

    companion object {
        private val prefix = Regex("^[A-Za-z]{2,3}\\s*[:|]\\s*")            // "IN: Star Plus", "UK | BBC One"
        private val bracket = Regex("\\s*[(\\[][^)\\]]*[)\\]]\\s*")             // "Star Plus (India)"
        private val domain = Regex("\\.[a-z]{2}$")                              // "StarPlus.in"
        private val quality = Regex("\\b(hd|fhd|uhd|sd|4k|hevc|tv)\\b")

        fun key(name: String): String =
            name.lowercase().replace(quality, " ").replace(Regex("[^\\p{L}\\p{N}]"), "")

        fun candidates(name: String): List<String> {
            val cleaned = name.trim().replace(prefix, "").replace(bracket, " ").replace(domain, "")
            return listOf(key(name), key(cleaned)).filter { it.length >= 2 }.distinct()
        }

        fun build(channels: List<IptvLogoChannel>, logos: List<IptvLogo>): LogoIndex {
            val logoById = HashMap<String, String>()
            logos.forEach { l -> if (l.feed == null || !logoById.containsKey(l.channel)) logoById[l.channel] = l.url }
            val map = HashMap<String, MutableList<Pair<String, String>>>()
            channels.forEach { c ->
                if (c.isNsfw) return@forEach
                val logo = logoById[c.id] ?: return@forEach
                (listOf(c.name) + c.altNames).flatMap { candidates(it) }.distinct().forEach { k ->
                    map.getOrPut(k) { ArrayList(1) }.add(c.country to logo)
                }
            }
            return LogoIndex(map)
        }
    }
}
