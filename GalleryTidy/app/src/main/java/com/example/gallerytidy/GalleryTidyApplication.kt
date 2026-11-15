package com.example.gallerytidy

import android.app.Application
import com.example.gallerytidy.data.GalleryDatabase
import com.example.gallerytidy.data.PhotoRepository
import com.example.gallerytidy.data.ScanScheduler

class GalleryTidyApplication : Application() {
    val database by lazy { GalleryDatabase.build(this) }
    val repository by lazy { PhotoRepository(this, database.photoDao()) }

    override fun onCreate() {
        super.onCreate()
        ScanScheduler.schedulePeriodic(this)
    }
}
