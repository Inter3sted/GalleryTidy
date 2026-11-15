package com.example.gallerytidy.analysis

import com.example.gallerytidy.data.PhotoEntity
import kotlin.math.abs

data class DuplicateGroup(val keep: PhotoEntity, val extras: List<PhotoEntity>)

/**
 * Groups near-identical shots (bursts, retakes). Only photos taken close in time are compared,
 * so cost is O(n * LOOKAHEAD) instead of O(n^2) on big libraries.
 */
object DuplicateFinder {

    private const val WINDOW_MS = 10 * 60_000L
    private const val LOOKAHEAD = 40

    fun findGroups(photos: List<PhotoEntity>, maxDistance: Int): List<DuplicateGroup> {
        // Hashes that are almost all 0s or 1s come from flat images and match everything.
        val sorted = photos
            .filter { it.dHash != null && java.lang.Long.bitCount(it.dHash) in 6..58 }
            .sortedBy { it.dateTaken }
        val parent = IntArray(sorted.size) { it }

        fun find(x: Int): Int {
            var a = x
            while (parent[a] != a) {
                parent[a] = parent[parent[a]]
                a = parent[a]
            }
            return a
        }

        for (i in sorted.indices) {
            val a = sorted[i]
            var j = i + 1
            while (j < sorted.size && j <= i + LOOKAHEAD) {
                val b = sorted[j]
                if (b.dateTaken - a.dateTaken > WINDOW_MS) break
                if (sameShape(a, b) &&
                    java.lang.Long.bitCount(a.dHash!! xor b.dHash!!) <= maxDistance
                ) {
                    parent[find(j)] = find(i)
                }
                j++
            }
        }

        return sorted.indices
            .groupBy { find(it) }
            .values
            .filter { it.size >= 2 }
            .map { idx ->
                val members = idx.map { sorted[it] }
                val keep = members.maxWith(
                    compareBy<PhotoEntity>({ it.width.toLong() * it.height }, { it.sharpness ?: 0.0 }, { it.sizeBytes })
                )
                DuplicateGroup(keep, members.filter { it.id != keep.id })
            }
            .sortedByDescending { it.keep.dateTaken }
    }

    private fun sameShape(a: PhotoEntity, b: PhotoEntity): Boolean {
        if (a.height == 0 || b.height == 0) return false
        return abs(a.width.toDouble() / a.height - b.width.toDouble() / b.height) < 0.05
    }
}
