package com.example.gallerytidy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached MediaStore row + analysis results.
 * Flags (blurry / dark / bright / duplicate) are NOT stored: they are derived from
 * the raw metrics at read time, so changing a threshold slider is instant and never
 * requires re-analysing the library.
 */
@Entity(tableName = "photos", indices = [Index("dateTaken"), Index("analyzedAt")])
data class PhotoEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String?,
    val dateTaken: Long,      // ms
    val dateModified: Long,   // s, straight from MediaStore (change detection)
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val folder: String?,

    val sharpness: Double? = null,   // tiled Laplacian variance, higher = sharper
    val brightness: Double? = null,  // mean luma 0..255
    val dHash: Long? = null,         // 64-bit difference hash
    val analyzedAt: Long? = null,    // null = still pending
    val dismissed: Boolean = false   // user pressed "Keep"
)

data class IdAndModified(val id: Long, val dateModified: Long)
