package com.worldtv.guide

import android.app.Application
import com.worldtv.guide.data.Prefs
import com.worldtv.guide.data.TvRepository

class WorldTvApp : Application() {
    lateinit var repo: TvRepository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repo = TvRepository(cacheDir)
        prefs = Prefs(this)
    }
}
