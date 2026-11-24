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

@Serializable
data class IptvFeed(
    val channel: String,
    val id: String = "",
    @SerialName("is_main") val isMain: Boolean = false,
    val languages: List<String> = emptyList(),
)

@Serializable
data class IptvLanguage(val code: String, val name: String)

/** What the iptv-org database knows about a channel. */
class ChannelInfo(val country: String, val logo: String?, val languages: List<String>)

/**
 * Looks channels up by name in the iptv-org database to find a logo and broadcast
 * languages, for guides that don't supply them.
 */
class LogoIndex(private val byName: Map<String, List<ChannelInfo>>) {

    /** [countryCode] is an iptv-org code ("UK" for the United Kingdom). */
    fun info(channelName: String, countryCode: String?): ChannelInfo? {
        for (key in candidates(channelName)) {
            val hits = byName[key] ?: continue
            return hits.firstOrNull { it.country == countryCode } ?: hits.first()
        }
        return null
    }

    fun find(channelName: String, countryCode: String?): String? {
        for (key in candidates(channelName)) {
            val hits = byName[key] ?: continue
            return (hits.firstOrNull { it.country == countryCode && it.logo != null } ?: hits.firstOrNull { it.logo != null })?.logo
                ?: continue
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

        fun build(
            channels: List<IptvLogoChannel>, logos: List<IptvLogo>,
            feeds: List<IptvFeed>, languages: List<IptvLanguage>,
        ): LogoIndex {
            val logoById = HashMap<String, String>()
            logos.forEach { l -> if (l.feed == null || !logoById.containsKey(l.channel)) logoById[l.channel] = l.url }
            val langName = languages.associate { it.code to it.name }
            // Prefer the main feed's languages; otherwise any feed's.
            val langsById = HashMap<String, List<String>>()
            feeds.sortedByDescending { it.isMain }.forEach { f ->
                if (f.languages.isNotEmpty() && !langsById.containsKey(f.channel)) {
                    langsById[f.channel] = f.languages.map { langName[it] ?: it }
                }
            }
            val map = HashMap<String, MutableList<ChannelInfo>>()
            channels.forEach { c ->
                if (c.isNsfw) return@forEach
                val logo = logoById[c.id]
                val langs = langsById[c.id].orEmpty()
                if (logo == null && langs.isEmpty()) return@forEach
                val info = ChannelInfo(c.country, logo, langs)
                (listOf(c.name) + c.altNames).flatMap { candidates(it) }.distinct().forEach { k ->
                    map.getOrPut(k) { ArrayList(1) }.add(info)
                }
            }
            return LogoIndex(map)
        }
    }
}

/** Guesses a channel's language from its name, for well-known Indian channel families. */
object LanguageGuess {
    private val rules: List<Pair<String, Regex>> = listOf(
        "Tamil" to "tamil|sun tv|sun music|sun news|\\bktv\\b|adithya|chutti|vijay|jaya (tv|max|plus|movies)|kalaignar|polimer|raj (tv|digital|musix|news)|puthiya thalaimurai|thanthi|sathiyam|murasu|isaiaruvi|vasanth|zee thirai|j movies|mega tv|news ?7 tamil|kalaignar",
        "Telugu" to "telugu|gemini|star maa|\\bmaa (tv|gold|movies|music)|\\betv\\b(?! (bal|urdu))|cinemalu|sakshi|\\bntv\\b|tv ?9 telugu|\\btv ?5\\b|\\babn\\b|10 ?tv|t news|\\bv6\\b|hmtv|mahaa|bhakthi",
        "Kannada" to "kannada|udaya|suvarna|colors super|public tv|chintu|zee picchar|tv ?9 kannada",
        "Malayalam" to "malayalam|asianet|surya (tv|movies|music|comedy)|kairali|mazhavil|manorama|flowers tv|amrita tv|mathrubhumi|kochu tv|jaihind|media ?one|24 news|reporter tv",
        "Marathi" to "marathi|zee yuva|zee talkies|\\bsaam\\b|lokshahi|abp majha|pravah|fakt marathi|zee 24 taas",
        "Bengali" to "bangla|bengali|jalsha|aakash aath|sangeet bangla|abp ananda|kolkata tv|ruposhi|\\br plus\\b|enterr? ?10 bangla",
        "Gujarati" to "gujarati|gujarat|sandesh news|\\bgstv\\b|\\bvtv\\b",
        "Punjabi" to "punjabi|\\bptc\\b|pitaara|chardikla|\\bmh ?1\\b",
        "Odia" to "odia|oriya|tarang|sarthak|alankar|kanak news|\\botv\\b|prarthana|manjari",
        "Bhojpuri" to "bhojpuri|big ganga|anjan tv",
        "Assamese" to "assam|rengoni|\\brang\\b|pratidin time|news live",
        "Urdu" to "urdu|zee salaam|munsif",
        "English" to "english|\\bwion\\b|times now|mirror now|ndtv 24x7|cnn news18|republic tv|india today|et now|cnbc tv18|zee cafe|comedy central|colors infinity|\\bmn\\+|movies now|romedy now|star movies|sony pix|\\bhbo\\b|bbc world|\\bcnn\\b|al jazeera|dd india",
        "Hindi" to "hindi|star plus|star bharat|star gold|sony (sab|entertainment|max|set)|\\bset\\b|zee (tv|cinema|anmol|news|action|classic|bollywood)|colors (tv|cineplex|rishtey)?$|\\bcolors\\b(?! (tamil|kannada|bangla|marathi|gujarati|odia|super|infinity))|&tv|and ?tv|&pictures|aaj tak|abp news|ndtv india|news18 india|india tv|republic bharat|dd national|dangal|shemaroo|b4u|zoom|9x|sony wah|zing|bindass",
    ).map { (lang, pattern) -> lang to Regex(pattern, RegexOption.IGNORE_CASE) }

    fun fromName(name: String): String? = rules.firstOrNull { it.second.containsMatchIn(name) }?.first
}
