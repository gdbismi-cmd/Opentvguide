package com.worldtv.guide

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import com.worldtv.guide.data.CachedHttp
import okhttp3.OkHttpClient
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.TvRepository
import com.worldtv.guide.ui.theme.Palettes
import com.worldtv.guide.ui.theme.Snap

class WorldTvApp : Application(), ImageLoaderFactory {
    lateinit var repo: TvRepository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repo = TvRepository(cacheDir)
        prefs = Prefs(this)
        Snap.palette = Palettes.byId(prefs.themeId)
    }

    /** App-wide image loader: SVG logos, browser User-Agent (some logo hosts block apps), disk cache. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", CachedHttp.USER_AGENT).build())
                }
                .build()
        }
        .components { add(SvgDecoder.Factory()) }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(80L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
