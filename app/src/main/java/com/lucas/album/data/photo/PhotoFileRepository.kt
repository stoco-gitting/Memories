package com.lucas.album.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PhotoFileRepository(private val context: Context) {

    private val photosDir: File by lazy {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    data class CopiedPhoto(val fileName: String, val aspectRatio: Float)

    suspend fun copyToInternalStorage(uri: Uri): CopiedPhoto? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(width, height, MAX_DIMENSION)
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        val fileName = "${UUID.randomUUID()}.jpg"
        FileOutputStream(File(photosDir, fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        bitmap.recycle()

        return CopiedPhoto(fileName, aspectRatio)
    }

    fun fileFor(fileName: String): File = File(photosDir, fileName)

    fun delete(fileName: String) {
        fileFor(fileName).delete()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val longest = maxOf(width, height)
        while (longest / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        private const val MAX_DIMENSION = 2048
    }
}
