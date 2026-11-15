package com.example.gallerytidy.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-Kotlin image metrics (no OpenCV: avoids ~30 MB of native libs and 16 KB page-size issues).
 * One decode gives sharpness, brightness and a perceptual hash.
 */
object ImageAnalyzer {

    private const val MAX_EDGE = 1024
    private const val GRID = 4

    data class Metrics(val sharpness: Double, val brightness: Double, val dHash: Long)

    /** Decodes at reduced size (honours EXIF rotation, supports HEIC) into a software bitmap. */
    fun decode(context: Context, uri: Uri): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val s = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * s).roundToInt().coerceAtLeast(1),
                    (info.size.height * s).roundToInt().coerceAtLeast(1)
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    fun analyze(bitmap: Bitmap): Metrics? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return null

        val luma = IntArray(w * h)
        bitmap.getPixels(luma, 0, w, 0, 0, w, h)
        var total = 0L
        for (i in luma.indices) {
            val p = luma[i]
            val l = (77 * ((p shr 16) and 0xFF) + 150 * ((p shr 8) and 0xFF) + 29 * (p and 0xFF)) shr 8
            luma[i] = l
            total += l
        }
        return Metrics(
            sharpness = tiledSharpness(luma, w, h),
            brightness = total.toDouble() / luma.size,
            dHash = differenceHash(luma, w, h)
        )
    }

    /**
     * Variance of the Laplacian per tile; the score is the mean of the 3 sharpest tiles (of 16).
     * A global variance wrongly flags portraits with blurred backgrounds; this doesn't.
     */
    private fun tiledSharpness(l: IntArray, w: Int, h: Int): Double {
        val n = GRID * GRID
        val sum = DoubleArray(n)
        val sumSq = DoubleArray(n)
        val cnt = IntArray(n)
        for (y in 1 until h - 1) {
            val row = y * w
            val ty = y * GRID / h
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = 4 * l[i] - l[i - 1] - l[i + 1] - l[i - w] - l[i + w]
                val t = ty * GRID + x * GRID / w
                sum[t] += lap.toDouble()
                sumSq[t] += (lap * lap).toDouble()
                cnt[t]++
            }
        }
        val variances = (0 until n)
            .filter { cnt[it] > 0 }
            .map { t ->
                val mean = sum[t] / cnt[t]
                sumSq[t] / cnt[t] - mean * mean
            }
            .sortedDescending()
        if (variances.isEmpty()) return 0.0
        return variances.take(3).average()
    }

    /** 9x8 box-averaged grid; bit set when a cell is brighter than its right neighbour. */
    private fun differenceHash(l: IntArray, w: Int, h: Int): Long {
        val avg = Array(8) { DoubleArray(9) }
        for (cy in 0 until 8) {
            val y0 = cy * h / 8
            val y1 = max(y0 + 1, (cy + 1) * h / 8)
            for (cx in 0 until 9) {
                val x0 = cx * w / 9
                val x1 = max(x0 + 1, (cx + 1) * w / 9)
                var s = 0L
                for (y in y0 until y1) for (x in x0 until x1) s += l[y * w + x]
                avg[cy][cx] = s.toDouble() / ((y1 - y0) * (x1 - x0))
            }
        }
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (avg[y][x] > avg[y][x + 1]) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }
}
