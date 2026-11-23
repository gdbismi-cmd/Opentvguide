package com.worldtv.guide.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- TVmaze (schedules / programmes) ----------

@Serializable
data class TmImage(val medium: String? = null, val original: String? = null)

@Serializable
data class TmCountry(val name: String? = null, val code: String? = null, val timezone: String? = null)

@Serializable
data class TmNetwork(
    val id: Int = 0,
    val name: String = "",
    val country: TmCountry? = null,
    val officialSite: String? = null,
)

@Serializable
data class TmRating(val average: Double? = null)

@Serializable
data class TmShow(
    val id: Int = 0,
    val name: String = "",
    val type: String? = null,
    val language: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val runtime: Int? = null,
    val officialSite: String? = null,
    val network: TmNetwork? = null,
    val webChannel: TmNetwork? = null,
    val image: TmImage? = null,
    val summary: String? = null,
    val rating: TmRating? = null,
)

@Serializable
data class TmEmbedded(val show: TmShow? = null)

@Serializable
data class TmEpisode(
    val id: Long = 0,
    val name: String? = null,
    val season: Int? = null,
    val number: Int? = null,
    val type: String? = null,
    val airdate: String? = null,
    val airtime: String? = null,
    val airstamp: String? = null,
    val runtime: Int? = null,
    val image: TmImage? = null,
    val summary: String? = null,
    // /schedule puts the show here …
    val show: TmShow? = null,
    // … while /schedule/web and ?embed=show put it here.
    @SerialName("_embedded") val embedded: TmEmbedded? = null,
) {
    val theShow: TmShow? get() = show ?: embedded?.show
    val channel: TmNetwork? get() = theShow?.network ?: theShow?.webChannel
    val channelName: String get() = channel?.name ?: "Unknown channel"
}

// ---------- iptv-org (country list) ----------

@Serializable
data class IptvCountry(
    val name: String,
    val code: String,
    val languages: List<String> = emptyList(),
    val flag: String = "",
)


// ---------- App models ----------

data class Country(val code: String, val name: String, val flag: String) {
    /** iptv-org uses "UK"; TVmaze (and ISO 3166) use "GB". */
    val isoCode: String get() = if (code == "UK") "GB" else code
}

/** Special pseudo-country used for TVmaze's streaming-service schedule. */
val STREAMING = Country(code = "WEB", name = "Streaming services (worldwide)", flag = "🌐")
