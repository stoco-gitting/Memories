package com.lucas.album

import android.app.Application
import com.lucas.album.data.AppContainer

class AlbumApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
