package com.rosebank.pitchpulse

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Tiny document database. Each document is a JSON file under
 * files/nosql/{collection}/{id}.json — not a SQL row.
 * Reference: Android Developers, 2025. Data and file storage overview (app-specific storage).
 */
object DocumentStore {
    private const val TAG = "PP-NoSQL"

    fun put(context: Context, collection: String, id: String, json: JSONObject) {
        Log.d(TAG, "WRITE $collection/$id")
        file(context, collection, id).writeText(json.toString(2))
    }

    fun get(context: Context, collection: String, id: String): JSONObject? {
        Log.d(TAG, "READ $collection/$id")
        val target = file(context, collection, id)
        if (!target.exists()) return null
        return JSONObject(target.readText())
    }

    fun list(context: Context, collection: String): List<JSONObject> {
        val folder = dir(context, collection)
        if (!folder.exists()) return emptyList()
        return folder.listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { JSONObject(it.readText()) }
            ?: emptyList()
    }

    private fun dir(context: Context, collection: String): File {
        val folder = File(context.filesDir, "nosql/${safe(collection)}")
        folder.mkdirs()
        return folder
    }

    private fun file(context: Context, collection: String, id: String): File {
        return File(dir(context, collection), "${safe(id)}.json")
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
