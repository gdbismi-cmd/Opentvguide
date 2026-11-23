package com.worldtv.guide.util

import androidx.core.text.HtmlCompat
import com.worldtv.guide.data.TmEpisode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun String?.stripHtml(): String =
    if (this.isNullOrBlank()) "" else HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

val TmEpisode.startInstant: Instant?
    get() = airstamp?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

val TmEpisode.durationMin: Int
    get() = runtime ?: theShow?.runtime ?: 30

val TmEpisode.endInstant: Instant?
    get() = startInstant?.plusSeconds(durationMin * 60L)

fun TmEpisode.isOnNow(now: Instant = Instant.now()): Boolean {
    val s = startInstant ?: return false
    val e = endInstant ?: return false
    return !now.isBefore(s) && now.isBefore(e)
}

fun TmEpisode.hasEnded(now: Instant = Instant.now()): Boolean = endInstant?.isBefore(now) == true

/** 0..1 progress through the programme, if it is airing now. */
fun TmEpisode.progress(now: Instant = Instant.now()): Float {
    val s = startInstant ?: return 0f
    val total = durationMin * 60f
    return ((now.epochSecond - s.epochSecond) / total).coerceIn(0f, 1f)
}

private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE d MMM, ")

fun Instant.localTime(): String = timeFmt.format(atZone(ZoneId.systemDefault()))
fun Instant.localDateTime(): String =
    dateTimeFmt.format(atZone(ZoneId.systemDefault())) + localTime()

fun TmEpisode.episodeLabel(): String {
    val se = if (season != null && number != null) "S%02dE%02d".format(season, number) else null
    val title = name?.takeIf { it.isNotBlank() && it != theShow?.name }
    return listOfNotNull(se, title).joinToString(" · ")
}
