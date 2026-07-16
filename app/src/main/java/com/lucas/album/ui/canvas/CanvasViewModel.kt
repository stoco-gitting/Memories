package com.lucas.album.ui.canvas

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucas.album.data.backup.BackupRepository
import com.lucas.album.data.local.PhotoLayerDao
import com.lucas.album.data.local.PhotoLayerEntity
import com.lucas.album.data.photo.PhotoFileRepository
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

// `_layers` is the ViewModel's own live state (seeded from Room, mutated immediately on
// every gesture callback for instant visual feedback); Room writes are debounced
// separately. Reading straight from dao.observeAll() as the render source instead would
// gate on-screen movement behind each debounced write, making drags feel laggy.
class CanvasViewModel(
    private val dao: PhotoLayerDao,
    private val photoFileRepository: PhotoFileRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _layers = MutableStateFlow<List<PhotoLayerEntity>>(emptyList())
    val layers: StateFlow<List<PhotoLayerEntity>> = _layers.asStateFlow()

    private val _photoAddFailed = MutableStateFlow(false)
    val photoAddFailed: StateFlow<Boolean> = _photoAddFailed.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val debounceJobs = mutableMapOf<Long, Job>()
    private val pendingWrites = mutableMapOf<Long, PhotoLayerEntity>()

    sealed class BackupState {
        data object Idle : BackupState()
        data object Exporting : BackupState()
        data object Success : BackupState()
        data object Failure : BackupState()
    }

    sealed class ImportState {
        data object Idle : ImportState()
        data object Importing : ImportState()
        data class Success(val importedCount: Int, val skippedCount: Int) : ImportState()

        sealed class Failure : ImportState() {
            data object InvalidFile : Failure()
            data object UnsupportedVersion : Failure()
            data object InsufficientStorage : Failure()
            data object IoError : Failure()
        }
    }

    init {
        viewModelScope.launch {
            dao.observeAll().collect { fromDb -> _layers.value = fromDb }
        }
    }

    fun fileFor(layer: PhotoLayerEntity): File = photoFileRepository.fileFor(layer.fileName)

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var nextZ = dao.maxZIndex()
            var anySucceeded = false
            for (uri in uris) {
                val copied = photoFileRepository.copyToInternalStorage(uri) ?: continue
                anySucceeded = true
                nextZ += 1
                dao.insert(
                    PhotoLayerEntity(
                        fileName = copied.fileName,
                        posXFraction = 0.5f + (Random.nextFloat() - 0.5f) * 0.15f,
                        posYFraction = 0.5f + (Random.nextFloat() - 0.5f) * 0.15f,
                        rotationDegrees = (Random.nextFloat() - 0.5f) * 16f,
                        zIndex = nextZ,
                        aspectRatio = copied.aspectRatio,
                    )
                )
            }
            if (!anySucceeded) _photoAddFailed.value = true
        }
    }

    fun resetPhotoAddFailed() {
        _photoAddFailed.value = false
    }

    // Operates on `layerId` and looks up the current entity from `_layers.value` rather
    // than taking a whole PhotoLayerEntity — a caller's closured entity can be stale by
    // the time a gesture callback fires, which would silently revert other fields.
    fun onTransform(layerId: Long, posXFraction: Float, posYFraction: Float, scale: Float, rotationDegrees: Float) {
        val current = _layers.value.find { it.id == layerId } ?: return
        val updated = current.copy(
            posXFraction = posXFraction,
            posYFraction = posYFraction,
            scale = scale.coerceIn(0.3f, 4f),
            rotationDegrees = rotationDegrees,
        )
        updateLocal(updated)
        scheduleDebouncedSave(updated)
    }

    fun bringToFront(layerId: Long) {
        val current = _layers.value.find { it.id == layerId } ?: return
        val maxZ = _layers.value.maxOfOrNull { it.zIndex } ?: 0
        if (current.zIndex > maxZ) return
        val updated = current.copy(zIndex = maxZ + 1)
        updateLocal(updated)
        scheduleDebouncedSave(updated)
    }

    fun setCaption(layerId: Long, caption: String) {
        val current = _layers.value.find { it.id == layerId } ?: return
        val updated = current.copy(caption = caption.take(80))
        updateLocal(updated)
        viewModelScope.launch { dao.update(updated) }
    }

    fun deleteLayer(layerId: Long) {
        val current = _layers.value.find { it.id == layerId } ?: return
        debounceJobs.remove(layerId)?.cancel()
        pendingWrites.remove(layerId)
        _layers.value = _layers.value.filterNot { it.id == layerId }
        viewModelScope.launch {
            dao.delete(current)
            photoFileRepository.delete(current.fileName)
        }
    }

    private fun updateLocal(updated: PhotoLayerEntity) {
        _layers.value = _layers.value.map { if (it.id == updated.id) updated else it }
    }

    private fun scheduleDebouncedSave(updated: PhotoLayerEntity) {
        pendingWrites[updated.id] = updated
        debounceJobs[updated.id]?.cancel()
        debounceJobs[updated.id] = viewModelScope.launch {
            delay(400)
            dao.update(updated)
            pendingWrites.remove(updated.id)
        }
    }

    // Safety net for backgrounding mid-drag: cancel the delayed jobs (which haven't
    // written yet) and write the latest known value for each pending layer immediately.
    fun flushPendingSaves() {
        if (pendingWrites.isEmpty()) return
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        val toFlush = pendingWrites.values.toList()
        pendingWrites.clear()
        viewModelScope.launch {
            toFlush.forEach { dao.update(it) }
        }
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Exporting
            val success = backupRepository.exportBackup(destination, _layers.value)
            _backupState.value = if (success) BackupState.Success else BackupState.Failure
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupState.Idle
    }

    // Restored photos are appended, never replace what's already there — the current max
    // zIndex is read once so the whole imported batch layers sensibly on top of it.
    fun importBackup(source: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing
            when (val result = backupRepository.importBackup(source)) {
                is BackupRepository.ImportResult.Success -> {
                    if (result.layers.isNotEmpty()) {
                        val base = dao.maxZIndex()
                        dao.insertAll(result.layers.map { it.copy(zIndex = it.zIndex + base + 1) })
                    }
                    _importState.value = ImportState.Success(result.layers.size, result.skippedCount)
                }
                BackupRepository.ImportResult.InvalidFile -> _importState.value = ImportState.Failure.InvalidFile
                BackupRepository.ImportResult.UnsupportedVersion -> _importState.value = ImportState.Failure.UnsupportedVersion
                BackupRepository.ImportResult.InsufficientStorage -> _importState.value = ImportState.Failure.InsufficientStorage
                BackupRepository.ImportResult.IoError -> _importState.value = ImportState.Failure.IoError
            }
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    override fun onCleared() {
        flushPendingSaves()
        super.onCleared()
    }

    companion object {
        fun factory(
            dao: PhotoLayerDao,
            photoFileRepository: PhotoFileRepository,
            backupRepository: BackupRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CanvasViewModel(dao, photoFileRepository, backupRepository) as T
            }
        }
    }
}
