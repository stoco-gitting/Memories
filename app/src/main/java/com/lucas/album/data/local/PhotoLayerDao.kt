package com.lucas.album.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoLayerDao {
    @Query("SELECT * FROM photo_layers ORDER BY zIndex ASC")
    fun observeAll(): Flow<List<PhotoLayerEntity>>

    @Query("SELECT COALESCE(MAX(zIndex), 0) FROM photo_layers")
    suspend fun maxZIndex(): Int

    @Insert
    suspend fun insert(entity: PhotoLayerEntity): Long

    @Update
    suspend fun update(entity: PhotoLayerEntity)

    @Delete
    suspend fun delete(entity: PhotoLayerEntity)
}
