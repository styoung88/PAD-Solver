package com.example.phoneapp.capture

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Reads screenshots published after the current overlay session started. */
internal class ScreenshotSource(
    private val query: (Array<String>, String?, Array<String>?) -> Cursor?,
    private val apiLevel: Int = Build.VERSION.SDK_INT,
) {
    constructor(resolver: ContentResolver) : this({ columns, selection, args ->
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, selection, args, "_id DESC")
    })

    private var lastId = -1L

    fun startSession() {
        query(arrayOf("_id"), null, null)?.use {
            lastId = if (it.moveToFirst()) it.getLong(0) else -1L
        }
    }

    fun next(): Uri? {
        val columns = mutableListOf("_id", "_display_name")
        if (apiLevel >= 29) columns += "relative_path"
        val published = if (apiLevel >= 29) " AND is_pending = 0" else ""
        query(columns.toTypedArray(), "_id > ? AND _size > 0$published", arrayOf(lastId.toString()))?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1).orEmpty()
                val folder = if (apiLevel >= 29) cursor.getString(2).orEmpty() else ""
                if (name.contains("screenshot", true) || folder.contains("screenshots", true)) {
                    lastId = id
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }
        return null
    }
}
