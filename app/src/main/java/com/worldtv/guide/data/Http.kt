package com.worldtv.guide.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Tiny HTTP helper with a file cache. Fresh cache entries are returned without a
 * network call; stale ones are used as an offline fallback if the request fails.
 */
class CachedHttp(private val cacheDir: File) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, cacheKey: String, maxAgeMs: Long, force: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeMs
            if (fresh && !force) return@withContext file.readText()
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "WorldTVGuide-Android/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
                    val body = resp.body?.string() ?: throw IOException("Empty response")
                    runCatching { file.writeText(body) }
                    body
                }
            } catch (e: Exception) {
                if (file.exists()) file.readText() else throw e
            }
        }
}
