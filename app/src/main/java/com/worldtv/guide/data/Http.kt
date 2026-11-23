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

    /** Downloads [url] straight to a cache file (for large guide files) and returns it. */
    suspend fun getFile(url: String, maxAgeMs: Long, force: Boolean = false): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "feeds").apply { mkdirs() }
        val name = url.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60)
        val file = File(dir, "${Integer.toHexString(url.hashCode())}_$name")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeMs
        if (fresh && !force) return@withContext file
        try {
            val req = Request.Builder().url(url).header("User-Agent", "WorldTVGuide-Android/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty response")
                val tmp = File(dir, file.name + ".part")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
            pruneFeeds(dir)
            file
        } catch (e: Exception) {
            if (file.exists()) file else throw e
        }
    }

    /** Keeps the guide-file cache under ~300 MB and drops files older than 4 days. */
    private fun pruneFeeds(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        val cutoff = System.currentTimeMillis() - 4L * 24 * 60 * 60 * 1000
        var total = 0L
        for (f in files) {
            total += f.length()
            if (f.lastModified() < cutoff || total > 300L * 1024 * 1024) f.delete()
        }
    }
}
