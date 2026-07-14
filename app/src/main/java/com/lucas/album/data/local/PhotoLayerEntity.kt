package com.lucas.album.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_layers")
data class PhotoLayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val posXFraction: Float,
    val posYFraction: Float,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val zIndex: Int = 0,
    val caption: String? = null,
    val aspectRatio: Float = 1f,
)
