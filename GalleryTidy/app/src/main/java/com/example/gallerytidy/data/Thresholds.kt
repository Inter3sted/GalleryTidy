package com.example.gallerytidy.data

import android.content.Context

data class Thresholds(
    val blur: Float = 50f,            // sharpness below this = blurry
    val dark: Float = 30f,            // mean luma below this = too dark
    val bright: Float = 225f,         // mean luma above this = overexposed
    val duplicateDistance: Int = 6    // max dHash Hamming distance (0..64)
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val defaults = Thresholds()

    fun load() = Thresholds(
        blur = prefs.getFloat("blur", defaults.blur),
        dark = prefs.getFloat("dark", defaults.dark),
        bright = prefs.getFloat("bright", defaults.bright),
        duplicateDistance = prefs.getInt("dup", defaults.duplicateDistance)
    )

    var freedBytes: Long
        get() = prefs.getLong("freed", 0L)
        set(value) = prefs.edit().putLong("freed", value).apply()

    fun save(t: Thresholds) {
        prefs.edit()
            .putFloat("blur", t.blur)
            .putFloat("dark", t.dark)
            .putFloat("bright", t.bright)
            .putInt("dup", t.duplicateDistance)
            .apply()
    }
}
