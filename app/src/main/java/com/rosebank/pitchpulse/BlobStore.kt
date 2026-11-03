package com.rosebank.pitchpulse

import android.content.Context
import android.util.Log
import java.io.File

/**
 * App blob storage for images such as the profile photo.
 * Reference: Android Developers, 2025. Data and file storage overview (app-specific storage).
 */
object BlobStore {
    private const val TAG = "PP-Blob"

    fun save(context: Context, name: String, bytes: ByteArray): File {
        val folder = File(context.filesDir, "blobs")
        folder.mkdirs()
        val file = File(folder, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        file.writeBytes(bytes)
        Log.i(TAG, "Saved blob ${file.name} (${bytes.size} bytes)")
        return file
    }

    fun list(context: Context): List<File> {
        val folder = File(context.filesDir, "blobs")
        if (!folder.exists()) return emptyList()
        return folder.listFiles()?.sortedBy { it.name } ?: emptyList()
    }
}
