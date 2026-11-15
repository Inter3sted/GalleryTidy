package com.example.gallerytidy.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.gallerytidy.analysis.ImageAnalyzer
import com.example.gallerytidy.util.AccessLevel
import com.example.gallerytidy.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ScanProgress(val running: Boolean = false, val done: Int = 0, val total: Int = 0)

class PhotoRepository(
    private val context: Context,
    private val dao: PhotoDao
) {
    private val mutex = Mutex() // one scan at a time, whether from the UI or WorkManager
    private val _progress = MutableStateFlow(ScanProgress())

    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()
    val photos: Flow<List<PhotoEntity>> = dao.observeAll()

    /**
     * Syncs the cache with MediaStore, then analyses whatever is pending.
     * @return true when nothing is left to analyse, false if the deadline cut it short.
     */
    suspend fun refresh(deadlineMs: Long = Long.MAX_VALUE, onlyIfIdle: Boolean = false): Boolean {
        if (onlyIfIdle) {
            if (!mutex.tryLock()) return true // a scan is already running; nothing to do
        } else {
            mutex.lock()
        }
        try {
            if (Permissions.level(context) == AccessLevel.NONE) return true
            return withContext(Dispatchers.IO) {
                sync()
                analyzePending(deadlineMs)
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun dismiss(ids: List<Long>) = ids.chunked(500).forEach { dao.dismiss(it) }

    suspend fun remove(ids: List<Long>) = ids.chunked(500).forEach { dao.deleteByIds(it) }

    /** Diffs MediaStore against the cache using DATE_MODIFIED: new/edited rows are (re)queued, vanished rows dropped. */
    private suspend fun sync() {
        val known = dao.idsAndModified().associate { it.id to it.dateModified }
        val kept = dao.dismissedIds().toHashSet() // "Keep" survives the photo being edited
        val seen = HashSet<Long>(known.size + 64)
        val changed = ArrayList<PhotoEntity>()

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val cursor = context.contentResolver.query(
            collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        ) ?: return

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val folderCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                seen.add(id)
                val modified = c.getLong(modCol)
                if (known[id] == modified) continue
                val taken = c.getLong(takenCol)
                changed.add(
                    PhotoEntity(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        displayName = c.getString(nameCol),
                        dateTaken = if (taken > 0) taken else c.getLong(addedCol) * 1000,
                        dateModified = modified,
                        width = c.getInt(wCol),
                        height = c.getInt(hCol),
                        sizeBytes = c.getLong(sizeCol),
                        folder = c.getString(folderCol),
                        dismissed = id in kept
                    )
                )
            }
        }

        changed.chunked(500).forEach { dao.upsertAll(it) }
        val gone = known.keys.filter { it !in seen }
        if (gone.isNotEmpty()) remove(gone)
    }

    private suspend fun analyzePending(deadlineMs: Long): Boolean {
        val pending = dao.pendingAnalysis()
        if (pending.isEmpty()) {
            _progress.value = ScanProgress()
            return true
        }
        val gate = Semaphore(4)
        var done = 0
        _progress.value = ScanProgress(true, 0, pending.size)

        for (batch in pending.chunked(16)) {
            if (System.currentTimeMillis() > deadlineMs) {
                _progress.value = ScanProgress()
                return false
            }
            val results = coroutineScope {
                batch.map { p -> async(Dispatchers.Default) { gate.withPermit { analyzeOne(p) } } }.awaitAll()
            }
            dao.upsertAll(results)
            done += batch.size
            _progress.value = ScanProgress(true, done, pending.size)
        }
        _progress.value = ScanProgress()
        return true
    }

    private fun analyzeOne(photo: PhotoEntity): PhotoEntity {
        val now = System.currentTimeMillis()
        val bitmap = ImageAnalyzer.decode(context, android.net.Uri.parse(photo.uri))
            ?: return photo.copy(analyzedAt = now) // unreadable: mark done so it isn't retried forever
        val metrics = try {
            ImageAnalyzer.analyze(bitmap)
        } finally {
            bitmap.recycle()
        }
        return if (metrics == null) photo.copy(analyzedAt = now)
        else photo.copy(
            sharpness = metrics.sharpness,
            brightness = metrics.brightness,
            dHash = metrics.dHash,
            analyzedAt = now
        )
    }
}
