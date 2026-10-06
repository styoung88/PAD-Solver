package com.example.phoneapp.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri

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

        fun settingsIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()).apply {
                // AOSP Settings supports this highlight; other Settings apps may ignore it.
                // Direct permission-page intents require privileged system permissions.
                val highlightKey = ":settings:fragment_args_key"
                putExtra(highlightKey, "permission_settings")
                putExtra(":settings:show_fragment_args", Bundle().apply {
                    putString(highlightKey, "permission_settings")
                })
            }
    }
}

internal class ScreenshotShortcut(private val context: Context) {
    private val preferences = context.getSharedPreferences("capture_preferences", Context.MODE_PRIVATE)

    // Fresh installs opt in explicitly; preserve existing full-access users and saved choices.
    var enabled: Boolean
        get() = preferences.getBoolean("screenshot_shortcut", PhotoAccess.current(context) == PhotoAccess.FULL)
        set(value) { preferences.edit { putBoolean("screenshot_shortcut", value) } }

    var photoAccessRequested: Boolean
        get() = preferences.getBoolean("photo_access_requested", false)
        set(value) { preferences.edit { putBoolean("photo_access_requested", value) } }

    fun shouldOpenPhotoSettings(access: PhotoAccess, shouldShowRationale: Boolean): Boolean = when (access) {
        PhotoAccess.FULL -> false
        // Rerequesting selected access may only reopen photo selection, without an Allow all choice.
        PhotoAccess.SELECTED -> true
        PhotoAccess.NONE -> photoAccessRequested && !shouldShowRationale
    }

    fun canWatch(access: PhotoAccess): Boolean = enabled && access == PhotoAccess.FULL
}
