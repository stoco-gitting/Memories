package com.lucas.album.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PhotoLayerEntity::class], version = 1, exportSchema = false)
abstract class AlbumDatabase : RoomDatabase() {
    abstract fun photoLayerDao(): PhotoLayerDao

    companion object {
        fun build(context: Context): AlbumDatabase =
            Room.databaseBuilder(context.applicationContext, AlbumDatabase::class.java, "album.db")
                .build()
    }
}
