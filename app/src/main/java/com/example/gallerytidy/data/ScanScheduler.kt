package com.example.gallerytidy.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gallerytidy.GalleryTidyApplication
import java.util.concurrent.TimeUnit

class MediaScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as GalleryTidyApplication).repository
        val deadline = System.currentTimeMillis() + 8 * 60_000L // stay inside WorkManager's ~10 min limit
        val finished = repo.refresh(deadline, onlyIfIdle = true)
        return if (finished) Result.success() else Result.retry() // continue the backlog later
    }
}

object ScanScheduler {
    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        // Every 12 h: the foreground ContentObserver covers "just took a photo" while the app is open.
        val request = PeriodicWorkRequestBuilder<MediaScanWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("media_scan", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
