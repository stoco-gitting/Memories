package com.lucas.album.data.backup

import android.content.Context
import android.net.Uri
import com.lucas.album.data.local.PhotoLayerEntity
import com.lucas.album.data.photo.PhotoFileRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Moves potentially hundreds of MB across hundreds of files in one call — unlike the
// smaller PhotoFileRepository/PhotoExportRepository operations, an ANR is a real risk here
// without explicitly moving off the caller's dispatcher.
class BackupRepository(
    private val context: Context,
    private val photoFileRepository: PhotoFileRepository,
) {

    sealed class ImportResult {
        data class Success(val layers: List<PhotoLayerEntity>, val skippedCount: Int) : ImportResult()
        data object InvalidFile : ImportResult()
        data object UnsupportedVersion : ImportResult()
        data object InsufficientStorage : ImportResult()
        data object IoError : ImportResult()
    }

    suspend fun exportBackup(destination: Uri, layers: List<PhotoLayerEntity>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // A layer whose file is already missing shouldn't be referenced in the
                // manifest at all — a backup should only ever describe what it actually contains.
                val exportable = layers.filter { photoFileRepository.fileFor(it.fileName).exists() }
                val manifest = buildManifest(exportable)

                val outputStream = context.contentResolver.openOutputStream(destination) ?: return@withContext false
                outputStream.use { out ->
                    ZipOutputStream(out).use { zip ->
                        // Photos are already JPEG-compressed; default DEFLATE would just burn
                        // CPU/battery for near-zero size reduction.
                        zip.setLevel(Deflater.BEST_SPEED)

                        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        exportable.forEach { layer ->
                            zip.putNextEntry(ZipEntry("$PHOTOS_DIR_NAME/${layer.fileName}"))
                            photoFileRepository.fileFor(layer.fileName).inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                true
            } catch (e: IOException) {
                false
            }
        }

    suspend fun importBackup(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "backup_import_${UUID.randomUUID()}.zip")
        try {
            val inputStream = context.contentResolver.openInputStream(source) ?: return@withContext ImportResult.IoError
            inputStream.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }

            ZipFile(tempFile).use { zip ->
                val photoEntries = zip.entries().asSequence()
                    .filter { it.name.startsWith("$PHOTOS_DIR_NAME/") }
                    .toList()
                val totalPhotoBytes = photoEntries.sumOf { it.size.coerceAtLeast(0) }
                if (context.filesDir.usableSpace < totalPhotoBytes + MIN_FREE_SPACE_BUFFER_BYTES) {
                    return@withContext ImportResult.InsufficientStorage
                }

                val manifestEntry = zip.getEntry(MANIFEST_ENTRY_NAME) ?: return@withContext ImportResult.InvalidFile
                if (manifestEntry.size > MAX_MANIFEST_SIZE_BYTES) return@withContext ImportResult.InvalidFile

                val manifestText = zip.getInputStream(manifestEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                val manifest = JSONObject(manifestText)

                val formatVersion = manifest.optInt(KEY_FORMAT_VERSION, -1)
                if (formatVersion == -1) return@withContext ImportResult.InvalidFile
                if (formatVersion > CURRENT_FORMAT_VERSION) return@withContext ImportResult.UnsupportedVersion

                val layersJson = manifest.optJSONArray(KEY_LAYERS) ?: return@withContext ImportResult.InvalidFile

                val restoredLayers = mutableListOf<PhotoLayerEntity>()
                var skippedCount = 0

                for (i in 0 until layersJson.length()) {
                    val row = layersJson.optJSONObject(i)
                    val originalFileName = row?.optString(KEY_FILE_NAME, "").orEmpty()
                    val photoEntry = if (originalFileName.isNotBlank()) zip.getEntry("$PHOTOS_DIR_NAME/$originalFileName") else null

                    if (row == null || photoEntry == null) {
                        skippedCount++
                        continue
                    }

                    // Never reuse the original filename: fileName has no uniqueness constraint
                    // in Room, and deleteLayer() deletes-by-filename unconditionally — two rows
                    // ever sharing one physical file would let deleting one silently break the
                    // other. A fresh UUID per imported photo keeps that 1-file-per-row invariant.
                    val newFileName = "${UUID.randomUUID()}.jpg"
                    try {
                        zip.getInputStream(photoEntry).use { input ->
                            photoFileRepository.fileFor(newFileName).outputStream().use { output -> input.copyTo(output) }
                        }
                    } catch (e: IOException) {
                        skippedCount++
                        continue
                    }

                    restoredLayers += PhotoLayerEntity(
                        fileName = newFileName,
                        posXFraction = row.optDouble(KEY_POS_X, 0.5).toFloat().coerceIn(0f, 1f),
                        posYFraction = row.optDouble(KEY_POS_Y, 0.5).toFloat().coerceIn(0f, 1f),
                        scale = row.optDouble(KEY_SCALE, 1.0).toFloat().coerceIn(0.3f, 4f),
                        rotationDegrees = row.optDouble(KEY_ROTATION, 0.0).toFloat(),
                        zIndex = row.optInt(KEY_Z_INDEX, 0),
                        caption = if (row.isNull(KEY_CAPTION)) null else row.optString(KEY_CAPTION, "").take(80).ifBlank { null },
                        aspectRatio = row.optDouble(KEY_ASPECT_RATIO, 1.0).toFloat(),
                    )
                }

                ImportResult.Success(restoredLayers, skippedCount)
            }
        } catch (e: ZipException) {
            ImportResult.InvalidFile
        } catch (e: JSONException) {
            ImportResult.InvalidFile
        } catch (e: IOException) {
            ImportResult.IoError
        } finally {
            tempFile.delete()
        }
    }

    private fun buildManifest(layers: List<PhotoLayerEntity>): JSONObject = JSONObject().apply {
        put(KEY_FORMAT_VERSION, CURRENT_FORMAT_VERSION)
        put(KEY_EXPORTED_AT, System.currentTimeMillis())
        put(KEY_PHOTO_COUNT, layers.size)
        put(
            KEY_LAYERS,
            JSONArray().apply {
                layers.forEach { layer ->
                    put(
                        JSONObject().apply {
                            put(KEY_FILE_NAME, layer.fileName)
                            put(KEY_POS_X, layer.posXFraction.toDouble())
                            put(KEY_POS_Y, layer.posYFraction.toDouble())
                            put(KEY_SCALE, layer.scale.toDouble())
                            put(KEY_ROTATION, layer.rotationDegrees.toDouble())
                            put(KEY_Z_INDEX, layer.zIndex)
                            put(KEY_CAPTION, layer.caption ?: JSONObject.NULL)
                            put(KEY_ASPECT_RATIO, layer.aspectRatio.toDouble())
                        },
                    )
                }
            },
        )
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        private const val MANIFEST_ENTRY_NAME = "manifest.json"
        private const val PHOTOS_DIR_NAME = "photos"
        private const val MAX_MANIFEST_SIZE_BYTES = 50L * 1024 * 1024
        private const val MIN_FREE_SPACE_BUFFER_BYTES = 50L * 1024 * 1024

        private const val KEY_FORMAT_VERSION = "formatVersion"
        private const val KEY_EXPORTED_AT = "exportedAtEpochMillis"
        private const val KEY_PHOTO_COUNT = "photoCount"
        private const val KEY_LAYERS = "layers"
        private const val KEY_FILE_NAME = "fileName"
        private const val KEY_POS_X = "posXFraction"
        private const val KEY_POS_Y = "posYFraction"
        private const val KEY_SCALE = "scale"
        private const val KEY_ROTATION = "rotationDegrees"
        private const val KEY_Z_INDEX = "zIndex"
        private const val KEY_CAPTION = "caption"
        private const val KEY_ASPECT_RATIO = "aspectRatio"
    }
}
