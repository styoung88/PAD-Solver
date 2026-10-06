package com.example.phoneapp.capture

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotShortcutTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = target.getSharedPreferences("screenshot_shortcut_test", Context.MODE_PRIVATE)
    private val grants = mutableSetOf<String>()
    private val context = object : ContextWrapper(target) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission in grants) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        override fun getSharedPreferences(name: String, mode: Int) = preferences
    }

    @After fun cleanUp() { preferences.edit().clear().commit() }

    @Test fun losingFullAccessImmediatelyDisablesWatchingWithoutCachingTheGrant() {
        val shortcut = ScreenshotShortcut(context)
        shortcut.enabled = true
        val fullPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        grants += fullPermission
        assertTrue(shortcut.canWatch(PhotoAccess.current(context)))
        grants -= fullPermission
        if (Build.VERSION.SDK_INT >= 34) {
            grants += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            assertEquals(PhotoAccess.SELECTED, PhotoAccess.current(context))
            assertFalse(shortcut.canWatch(PhotoAccess.current(context)))
            grants.clear()
        }
        assertEquals(PhotoAccess.NONE, PhotoAccess.current(context))
        assertFalse(shortcut.canWatch(PhotoAccess.current(context)))
        grants += fullPermission
        assertTrue(shortcut.canWatch(PhotoAccess.current(context)))
    }

    @Test fun disablingShortcutPersistsAcrossReadersEvenWithFullPhotoAccess() {
        val shortcut = ScreenshotShortcut(context)
        shortcut.enabled = false
        assertFalse(ScreenshotShortcut(context).enabled)
        assertFalse(ScreenshotShortcut(context).canWatch(PhotoAccess.FULL))
        shortcut.enabled = true
        assertTrue(ScreenshotShortcut(context).canWatch(PhotoAccess.FULL))
        assertFalse(shortcut.canWatch(PhotoAccess.SELECTED))
        assertFalse(shortcut.canWatch(PhotoAccess.NONE))
    }

    @Test fun permissionRequestIncludesSelectedAccessOnSupportedDevices() {
        val permissions = PhotoAccess.requestedPermissions().toSet()
        val expected = when {
            Build.VERSION.SDK_INT >= 34 -> setOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> setOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> setOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        assertEquals(expected, permissions)
    }

    @Test fun onePhotoAccessControlRequestsInitiallyAndUsesSettingsWhenRequired() {
        val shortcut = ScreenshotShortcut(context)
        shortcut.photoAccessRequested = false
        assertFalse(shortcut.shouldOpenPhotoSettings(PhotoAccess.NONE, shouldShowRationale = false))
        shortcut.photoAccessRequested = true
        assertTrue(ScreenshotShortcut(context).shouldOpenPhotoSettings(PhotoAccess.NONE, shouldShowRationale = false))
        assertFalse(shortcut.shouldOpenPhotoSettings(PhotoAccess.NONE, shouldShowRationale = true))
        assertTrue(shortcut.shouldOpenPhotoSettings(PhotoAccess.FULL, shouldShowRationale = false))
        assertTrue(shortcut.shouldOpenPhotoSettings(PhotoAccess.FULL, shouldShowRationale = true))
    }

    @Test fun selectedPhotoAccessAlwaysOffersTheDialogAgain() {
        val shortcut = ScreenshotShortcut(context)
        for (previouslyRequested in listOf(false, true)) {
            shortcut.photoAccessRequested = previouslyRequested
            assertFalse(shortcut.shouldOpenPhotoSettings(PhotoAccess.SELECTED, shouldShowRationale = false))
            assertFalse(shortcut.shouldOpenPhotoSettings(PhotoAccess.SELECTED, shouldShowRationale = true))
        }
    }
}
