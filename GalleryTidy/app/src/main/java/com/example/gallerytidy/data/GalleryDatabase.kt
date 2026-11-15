package com.example.gallerytidy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PhotoEntity::class], version = 1, exportSchema = false)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        // The DB is only a cache of MediaStore, so rebuilding it is always safe.
        fun build(context: Context): GalleryDatabase =
            Room.databaseBuilder(context, GalleryDatabase::class.java, "gallery_tidy.db")
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
