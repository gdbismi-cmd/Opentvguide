package com.worldtv.guide.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

/**
 * Streaming XMLTV parser. Only programmes overlapping [from]..[to] are kept, so even
 * very large guide files can be read with little memory.
 */
object XmltvParser {

    private class RawProg(
        val channelId: String, val start: Instant, val end: Instant,
        val title: String, val subtitle: String?, val desc: String?,
        val categories: List<String>, val icon: String?, val episode: String?,
    )

    fun parseFile(
        file: File, from: Instant, to: Instant,
        sourceId: String, sourceName: String, limit: Int = 80_000,
    ): List<Programme> = openMaybeGzip(file).use { parse(it, from, to, sourceId, sourceName, limit) }

    private fun openMaybeGzip(file: File): InputStream {
        val bin = BufferedInputStream(file.inputStream(), 64 * 1024)
        bin.mark(2)
        val b1 = bin.read(); val b2 = bin.read()
        bin.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(bin, 64 * 1024), 64 * 1024) else bin
    }

    fun parse(
        input: InputStream, from: Instant, to: Instant,
        sourceId: String, sourceName: String, limit: Int = 80_000,
    ): List<Programme> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)

        val channelNames = HashMap<String, String>()
        val channelIcons = HashMap<String, String>()
        val raw = ArrayList<RawProg>()

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && raw.size < limit) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> readChannel(p, channelNames, channelIcons)
                    "programme" -> {
                        val start = parseTime(p.getAttributeValue(null, "start"))
                        val stop = parseTime(p.getAttributeValue(null, "stop"))
                        val ch = p.getAttributeValue(null, "channel")
                        if (start == null || ch == null) { skip(p) }
                        else {
                            val end = stop ?: start.plusSeconds(30 * 60)
                            if (end.isAfter(from) && start.isBefore(to)) readProgramme(p, ch, start, end)?.let { raw += it }
                            else skip(p)
                        }
                    }
                }
            }
            event = p.next()
        }

        return raw.map { r ->
            Programme(
                id = "x:${Integer.toHexString(sourceName.hashCode())}:${r.channelId}:${r.start.epochSecond}",
                title = r.title,
                subtitle = r.episode?.let { ep -> listOfNotNull(ep, r.subtitle).joinToString(" · ") } ?: r.subtitle,
                description = r.desc,
                channelName = channelNames[r.channelId] ?: r.channelId.substringBeforeLast('.').ifBlank { r.channelId },
                channelLogo = channelIcons[r.channelId],
                start = r.start, end = r.end,
                genres = r.categories,
                image = r.icon,
                sourceId = sourceId, sourceName = sourceName,
            )
        }.distinctBy { it.id }
    }

    private fun readChannel(p: XmlPullParser, names: MutableMap<String, String>, icons: MutableMap<String, String>) {
        val id = p.getAttributeValue(null, "id")
        val depth = p.depth
        var name: String? = null
        var icon: String? = null
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.START_TAG) when (p.name) {
                "display-name" -> { val t = readText(p); if (name == null && t.isNotBlank()) name = t }
                "icon" -> { if (icon == null) icon = p.getAttributeValue(null, "src") }
            }
        }
        if (id != null) {
            name?.let { names[id] = it }
            icon?.let { icons[id] = it }
        }
    }

    private fun readProgramme(p: XmlPullParser, ch: String, start: Instant, end: Instant): RawProg? {
        val depth = p.depth
        var title: String? = null
        var sub: String? = null
        var desc: String? = null
        var icon: String? = null
        var ep: String? = null
        val cats = ArrayList<String>(2)
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.START_TAG) when (p.name) {
                "title" -> { val t = readText(p); if (title == null && t.isNotBlank()) title = t }
                "sub-title" -> { val t = readText(p); if (sub == null && t.isNotBlank()) sub = t }
                "desc" -> { val t = readText(p); if (desc == null && t.isNotBlank()) desc = t }
                "category" -> { val t = readText(p); if (t.isNotBlank() && cats.size < 3 && t !in cats) cats += t }
                "icon" -> { if (icon == null) icon = p.getAttributeValue(null, "src") }
                "episode-num" -> {
                    val system = p.getAttributeValue(null, "system")
                    val t = readText(p)
                    if (ep == null) ep = formatEpisode(system, t)
                }
            }
        }
        val t = title ?: return null
        return RawProg(ch, start, end, t, sub, desc, cats, icon, ep)
    }

    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.TEXT || e == XmlPullParser.CDSECT) sb.append(p.text)
            if ((e == XmlPullParser.END_TAG && p.depth == depth) || e == XmlPullParser.END_DOCUMENT) break
        }
        return sb.toString().trim()
    }

    private fun skip(p: XmlPullParser) {
        var d = 1
        while (d > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> d++
                XmlPullParser.END_TAG -> d--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** "xmltv_ns" is zero-based "season.episode.part"; "onscreen" is shown as-is. */
    private fun formatEpisode(system: String?, text: String): String? {
        if (text.isBlank()) return null
        if (system == "xmltv_ns") {
            val parts = text.split('.')
            val s = parts.getOrNull(0)?.trim()?.substringBefore('/')?.toIntOrNull()
            val e = parts.getOrNull(1)?.trim()?.substringBefore('/')?.toIntOrNull()
            return when {
                s != null && e != null -> "S%02dE%02d".format(s + 1, e + 1)
                e != null -> "Ep ${e + 1}"
                else -> null
            }
        }
        return if (system == "onscreen" || system == null) text.takeIf { it.length <= 16 } else null
    }

    /** XMLTV time: "YYYYMMDDhhmmss +hhmm" (seconds and offset optional; no offset means UTC). */
    fun parseTime(s: String?): Instant? {
        if (s == null) return null
        val v = s.trim()
        if (v.length < 12) return null
        return runCatching {
            val y = v.substring(0, 4).toInt(); val mo = v.substring(4, 6).toInt(); val d = v.substring(6, 8).toInt()
            val h = v.substring(8, 10).toInt(); val mi = v.substring(10, 12).toInt()
            val sec = if (v.length >= 14 && v[12].isDigit()) v.substring(12, 14).toInt() else 0
            val off = v.substringAfter(' ', "").trim().let { o ->
                if (o.matches(Regex("[+-]\\d{4}"))) ZoneOffset.of(o) else ZoneOffset.UTC
            }
            LocalDateTime.of(y, mo, d, h, mi, sec).toInstant(off)
        }.getOrNull()
    }
}
