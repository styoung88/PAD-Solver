package com.example.phoneapp.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit

internal enum class PhotoAccess {
    FULL, SELECTED, NONE;

    companion object {
        fun fromPermissions(
            apiLevel: Int,
            imagesGranted: Boolean,
            selectedGranted: Boolean,
            storageGranted: Boolean,
        ): PhotoAccess = when {
            apiLevel >= 33 && imagesGranted -> FULL
            apiLevel >= 34 && selectedGranted -> SELECTED
            apiLevel <= 32 && storageGranted -> FULL
            else -> NONE
        }

        fun current(context: Context): PhotoAccess {
            fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            return fromPermissions(
                Build.VERSION.SDK_INT,
                Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES),
                Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
                Build.VERSION.SDK_INT <= 32 && granted(Manifest.permission.READ_EXTERNAL_STORAGE),
            )
        }

        fun requestedPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

internal class ScreenshotShortcut(context: Context) {
    private val preferences = context.getSharedPreferences("capture_preferences", Context.MODE_PRIVATE)

    // Retain the shortcut for existing users who already granted full access.
    var enabled: Boolean
        get() = preferences.getBoolean("screenshot_shortcut", true)
        set(value) { preferences.edit { putBoolean("screenshot_shortcut", value) } }

    var photoAccessRequested: Boolean
        get() = preferences.getBoolean("photo_access_requested", false)
        set(value) { preferences.edit { putBoolean("photo_access_requested", value) } }

    fun shouldOpenPhotoSettings(access: PhotoAccess, shouldShowRationale: Boolean): Boolean = when (access) {
        PhotoAccess.FULL -> true
        // Selected access supports the photo selection dialog again, regardless of rationale state.
        PhotoAccess.SELECTED -> false
        PhotoAccess.NONE -> photoAccessRequested && !shouldShowRationale
    }

    fun canWatch(access: PhotoAccess): Boolean = enabled && access == PhotoAccess.FULL
}
