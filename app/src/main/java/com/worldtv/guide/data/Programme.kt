package com.worldtv.guide.data

import com.worldtv.guide.util.stripHtml
import java.time.Instant

/** One airing on one channel, from any source (TVmaze or an XMLTV guide). */
data class Programme(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val channelName: String,
    val channelLogo: String? = null,
    val start: Instant,
    val end: Instant,
    val genres: List<String> = emptyList(),
    val image: String? = null,
    val sourceId: String,
    val sourceName: String,
    val tvmazeEpisodeId: Long? = null,
    val tvmazeShowId: Int? = null,
    /** The channel's language (e.g. "Hindi", "Tamil"), when known. */
    val language: String? = null,
) {
    val channelKey: String get() = normalizeChannel(channelName)

    /** Key used for "follow this show": TVmaze show id when known, otherwise the normalised title. */
    val favKey: String get() = tvmazeShowId?.let { "tm:$it" } ?: "t:${normalizeTitle(title)}"

    val durationMin: Int get() = ((end.epochSecond - start.epochSecond) / 60).toInt()

    fun isOnNow(now: Instant = Instant.now()) = !now.isBefore(start) && now.isBefore(end)
    fun hasEnded(now: Instant = Instant.now()) = !end.isAfter(now)
    fun progress(now: Instant = Instant.now()): Float {
        val total = (end.epochSecond - start.epochSecond).coerceAtLeast(1)
        return ((now.epochSecond - start.epochSecond).toFloat() / total).coerceIn(0f, 1f)
    }
}

private val qualitySuffix = Regex("\\b(hd|fhd|uhd|sd|4k|hevc)\\b")
private val nonAlnum = Regex("[^\\p{L}\\p{N}]")

fun normalizeChannel(name: String): String =
    name.lowercase().replace(qualitySuffix, " ").replace(nonAlnum, "")

fun normalizeTitle(title: String): String = title.lowercase().replace(nonAlnum, "")

/** Converts a TVmaze schedule entry into the common model. Returns null when it has no air time. */
fun TmEpisode.toProgramme(): Programme? {
    val start = airstamp?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: return null
    val show = theShow
    val minutes = runtime ?: show?.runtime ?: 30
    val se = if (season != null && number != null) "S%02dE%02d".format(season, number) else null
    val epTitle = name?.takeIf { it.isNotBlank() && it != show?.name }
    return Programme(
        id = "tm:$id",
        title = show?.name ?: name ?: "Untitled",
        subtitle = listOfNotNull(se, epTitle).joinToString(" · ").ifBlank { null },
        description = summary.stripHtml().ifBlank { show?.summary.stripHtml() }.ifBlank { null },
        channelName = channelName,
        start = start,
        end = start.plusSeconds(minutes * 60L),
        genres = show?.genres.orEmpty(),
        image = image?.medium ?: show?.image?.medium,
        sourceId = SourceIds.TVMAZE,
        sourceName = "TVmaze",
        tvmazeEpisodeId = id,
        tvmazeShowId = show?.id,
        language = show?.language,
    )
}
