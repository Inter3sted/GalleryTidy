package com.example.gallerytidy.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT id, dateModified FROM photos")
    suspend fun idsAndModified(): List<IdAndModified>

    @Query("SELECT id FROM photos WHERE dismissed = 1")
    suspend fun dismissedIds(): List<Long>

    @Query("SELECT * FROM photos WHERE analyzedAt IS NULL ORDER BY dateTaken DESC")
    suspend fun pendingAnalysis(): List<PhotoEntity>

    @Upsert
    suspend fun upsertAll(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE photos SET dismissed = 1 WHERE id IN (:ids)")
    suspend fun dismiss(ids: List<Long>)
}
