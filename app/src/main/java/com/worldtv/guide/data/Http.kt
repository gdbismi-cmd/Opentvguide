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
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun get(url: String, cacheKey: String, maxAgeMs: Long, force: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeMs
            if (fresh && !force) return@withContext file.readText()
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
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

    /**
     * Downloads a guide file straight to the cache and returns it. Tries [url] twice, then each
     * of [fallbacks]; if every address fails, an older cached copy is used when there is one.
     */
    suspend fun getFile(
        url: String, maxAgeMs: Long, force: Boolean = false, fallbacks: List<String> = emptyList(),
    ): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "feeds").apply { mkdirs() }
        val name = url.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60)
        val file = File(dir, "${Integer.toHexString(url.hashCode())}_$name")
        val fresh = file.exists() && file.length() > 0 && System.currentTimeMillis() - file.lastModified() < maxAgeMs
        if (fresh && !force) return@withContext file

        var lastError: Exception? = null
        val attempts = listOf(url, url) + fallbacks
        for ((i, u) in attempts.withIndex()) {
            try {
                download(u, File(dir, file.name + ".part"), file)
                pruneFeeds(dir)
                return@withContext file
            } catch (e: Exception) {
                lastError = e
                if (i == 0) kotlinx.coroutines.delay(1500)
            }
        }
        if (file.exists() && file.length() > 0) file else throw lastError ?: IOException("Download failed")
    }

    private fun download(url: String, tmp: File, dest: File) {
        val req = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/xml, application/gzip, text/xml, */*")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
            if (tmp.length() < 64) { tmp.delete(); throw IOException("Empty file") }
            // An HTML error page instead of a guide (e.g. a block page) should not replace a good copy.
            val head = tmp.inputStream().use { ByteArray(64).also { b -> it.read(b) } }
            val isGzip = head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()
            if (!isGzip && String(head).trimStart().startsWith("<!DOCTYPE html", ignoreCase = true)) {
                tmp.delete(); throw IOException("Got a web page instead of a guide")
            }
            if (!tmp.renameTo(dest)) { dest.delete(); tmp.renameTo(dest) }
        }
    }

    companion object {
        /** Some guide hosts refuse unknown clients, so present as a regular mobile browser. */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 WorldTVGuide/1.0"
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
