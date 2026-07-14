package com.lucas.album.data.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.lucas.album.data.local.PhotoLayerEntity

class PhotoExportRepository(private val context: Context) {

    suspend fun exportToGallery(
        layers: List<PhotoLayerEntity>,
        photoFileRepository: PhotoFileRepository,
        exportSize: Int = 1500,
    ): Boolean {
        val bitmap = Bitmap.createBitmap(exportSize, exportSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        for (layer in layers.sortedBy { it.zIndex }) {
            val file = photoFileRepository.fileFor(layer.fileName)
            if (!file.exists()) continue
            val photoBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue

            val centerX = layer.posXFraction * exportSize
            val centerY = layer.posYFraction * exportSize
            val matrix = Matrix().apply {
                postTranslate(-photoBitmap.width / 2f, -photoBitmap.height / 2f)
                postScale(layer.scale, layer.scale)
                postRotate(layer.rotationDegrees)
                postTranslate(centerX, centerY)
            }
            canvas.drawBitmap(photoBitmap, matrix, null)
            photoBitmap.recycle()
        }

        return saveToMediaStore(bitmap)
    }

    private fun saveToMediaStore(bitmap: Bitmap): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "album_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Album")
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } != null
    }
}
