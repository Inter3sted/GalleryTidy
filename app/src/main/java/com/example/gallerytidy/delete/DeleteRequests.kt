package com.example.gallerytidy.delete

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore

/**
 * Nothing is ever deleted silently: the system shows its own confirmation dialog
 * and only then removes the files (to the trash on Android 11+ devices that support it).
 */
object DeleteRequests {
    const val BATCH_SIZE = 200 // keeps the binder transaction comfortably small

    fun create(context: Context, uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
}
