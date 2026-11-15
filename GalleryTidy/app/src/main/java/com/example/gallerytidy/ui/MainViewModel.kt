package com.example.gallerytidy.ui

import android.app.Application
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gallerytidy.GalleryTidyApplication
import com.example.gallerytidy.analysis.DuplicateFinder
import com.example.gallerytidy.analysis.DuplicateGroup
import com.example.gallerytidy.data.PhotoEntity
import com.example.gallerytidy.data.ScanProgress
import com.example.gallerytidy.data.SettingsStore
import com.example.gallerytidy.data.Thresholds
import com.example.gallerytidy.delete.DeleteRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Category { BLURRY, DARK, BRIGHT, DUPLICATES }

data class UiState(
    val totalPhotos: Int = 0,
    val analyzedPhotos: Int = 0,
    val blurry: List<PhotoEntity> = emptyList(),
    val dark: List<PhotoEntity> = emptyList(),
    val bright: List<PhotoEntity> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val reclaimableBytes: Long = 0,
    val thresholds: Thresholds = Thresholds(),
    val progress: ScanProgress = ScanProgress(),
    val freedBytes: Long = 0
) {
    val duplicateExtras: List<PhotoEntity> get() = duplicateGroups.flatMap { it.extras }
}

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = getApplication<GalleryTidyApplication>().repository
    private val settings = SettingsStore(app)

    private val thresholds = MutableStateFlow(settings.load())
    private val freedBytes = MutableStateFlow(settings.freedBytes)

    private class Derived(
        val total: Int,
        val analyzed: Int,
        val blurry: List<PhotoEntity>,
        val dark: List<PhotoEntity>,
        val bright: List<PhotoEntity>,
        val groups: List<DuplicateGroup>,
        val reclaimable: Long
    )

    // Heavy work (duplicate grouping) depends only on photos + thresholds,
    // so scan-progress ticks and the freed counter never re-trigger it.
    private val derived = combine(repo.photos.debounce(250), thresholds) { photos, t ->
        val analyzed = photos.filter { it.analyzedAt != null && !it.dismissed }
        val measured = analyzed.filter { it.sharpness != null && it.brightness != null }

        val lo = t.dark.toDouble()
        val hi = t.bright.toDouble()
        val dark = measured.filter { it.brightness!! < lo }
        val bright = measured.filter { it.brightness!! > hi }
        val blurry = measured
            .filter { it.brightness!! in lo..hi && it.sharpness!! < t.blur }
            .sortedBy { it.sharpness }
        val groups = DuplicateFinder.findGroups(analyzed, t.duplicateDistance)

        val reclaimable = (blurry + dark + bright + groups.flatMap { it.extras })
            .distinctBy { it.id }
            .sumOf { it.sizeBytes }

        Derived(
            total = photos.size,
            analyzed = photos.count { it.analyzedAt != null },
            blurry = blurry, dark = dark, bright = bright,
            groups = groups, reclaimable = reclaimable
        )
    }.flowOn(Dispatchers.Default)

    val ui: StateFlow<UiState> = combine(derived, thresholds, repo.progress, freedBytes) { d, t, progress, freed ->
        UiState(
            totalPhotos = d.total,
            analyzedPhotos = d.analyzed,
            blurry = d.blurry, dark = d.dark, bright = d.bright,
            duplicateGroups = d.groups,
            reclaimableBytes = d.reclaimable,
            thresholds = t, progress = progress, freedBytes = freed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(thresholds = thresholds.value, freedBytes = freedBytes.value))

    // ---- scanning -------------------------------------------------------------------------

    private var refreshJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(debounceMs = 2_000)
    }

    init {
        app.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
    }

    /** Called on start, after permission changes, on "Rescan", and (debounced) when a photo is added. */
    fun refresh(debounceMs: Long = 0) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            repo.refresh()
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    // ---- settings -------------------------------------------------------------------------

    fun updateThresholds(t: Thresholds) {
        thresholds.value = t
        settings.save(t)
    }

    // ---- review actions -------------------------------------------------------------------

    fun keep(ids: List<Long>) {
        viewModelScope.launch { repo.dismiss(ids) }
    }

    private var deleteQueue: List<List<PhotoEntity>> = emptyList()
    private val _deleteIntents = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteIntents: SharedFlow<IntentSender> = _deleteIntents.asSharedFlow()

    fun requestDelete(photos: List<PhotoEntity>) {
        if (photos.isEmpty()) return
        deleteQueue = photos.chunked(DeleteRequests.BATCH_SIZE)
        launchNextDelete()
    }

    private fun launchNextDelete() {
        val batch = deleteQueue.firstOrNull() ?: return
        val uris = batch.map { Uri.parse(it.uri) }
        _deleteIntents.tryEmit(DeleteRequests.create(getApplication(), uris))
    }

    /** Called with the result of the system confirmation dialog. */
    fun onDeleteResult(confirmed: Boolean) {
        val batch = deleteQueue.firstOrNull() ?: return
        deleteQueue = if (confirmed) deleteQueue.drop(1) else emptyList()
        if (!confirmed) return
        viewModelScope.launch {
            repo.remove(batch.map { it.id })
            freedBytes.value += batch.sumOf { it.sizeBytes }
            settings.freedBytes = freedBytes.value
            launchNextDelete()
        }
    }
}
