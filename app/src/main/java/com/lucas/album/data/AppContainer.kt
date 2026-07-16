package com.lucas.album.data

import android.content.Context
import com.lucas.album.data.auth.PinManager
import com.lucas.album.data.backup.BackupRepository
import com.lucas.album.data.local.AlbumDatabase
import com.lucas.album.data.photo.PhotoFileRepository
import com.lucas.album.data.prefs.AppPreferences

class AppContainer(context: Context) {
    val preferences = AppPreferences(context)
    val pinManager = PinManager(preferences)
    private val database = AlbumDatabase.build(context)
    val photoLayerDao = database.photoLayerDao()
    val photoFileRepository = PhotoFileRepository(context)
    val backupRepository = BackupRepository(context, photoFileRepository)
}
