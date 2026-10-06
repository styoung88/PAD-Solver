package com.example.phoneapp.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.phoneapp.capture.PhotoAccess
import com.example.phoneapp.capture.ScreenshotShortcut
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScreenshotShortcutScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepScreenAwake() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test fun blockedDenialExplainsSettingsAndLetsUsersKeepUsingCapture() {
        var settings = 0
        var dismissed = 0
        compose.setContent { MaterialTheme {
            PhotoAccessSettingsDialog({ settings++ }, { dismissed++ })
        } }
        compose.onNodeWithText("Enable the screenshot shortcut").assertIsDisplayed()
        compose.onNodeWithText("Open Settings").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, settings) }
        compose.onNodeWithText("Keep using Capture").performClick()
        compose.runOnIdle {
            assertEquals(0, settings)
            assertEquals(1, dismissed)
        }
    }

    @Test fun blockedDenialOpensSettingsOnlyOnExplicitSelection() {
        var settings = 0
        compose.setContent { MaterialTheme { PhotoAccessSettingsDialog({ settings++ }, {}) } }
        compose.onNodeWithText("Open Settings").performClick()
        compose.runOnIdle { assertEquals(1, settings) }
    }

    @Test fun managingSelectedAccessShowsGuidanceBeforeOpeningSettings() {
        val helpVisible = mutableStateOf(false)
        var nativeRequests = 0
        var settings = 0
        val shortcut = ScreenshotShortcut(compose.activity)
        compose.setContent { MaterialTheme {
            HomeScreen({}, {}, true, PhotoAccess.SELECTED, {}, {
                if (shortcut.shouldOpenPhotoSettings(PhotoAccess.SELECTED, shouldShowRationale = false)) {
                    helpVisible.value = true
                } else nativeRequests++
            })
            if (helpVisible.value) PhotoAccessSettingsDialog({ settings++ }, { helpVisible.value = false })
        } }
        compose.onNodeWithText("Manage photo access").performScrollTo().performClick()
        compose.onNodeWithText("Enable the screenshot shortcut").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, nativeRequests)
            assertEquals(0, settings)
        }
        compose.onNodeWithText("Open Settings").performClick()
        compose.runOnIdle { assertEquals(1, settings) }
    }

    @Test fun selectedAccessExplainsTheLimitationButKeepsBothManualActionsAvailable() {
        showLimitedAccess(PhotoAccess.SELECTED, "This shortcut needs access to all photos to detect new screenshots. You’ve allowed selected photos only. You can still tap Capture.")
    }

    @Test fun deniedAccessExplainsTheLimitationButKeepsBothManualActionsAvailable() {
        showLimitedAccess(PhotoAccess.NONE, "To use this shortcut, allow access to all photos so we can detect new screenshots. Capture works without photo access.")
    }

    private fun showLimitedAccess(access: PhotoAccess, message: String) {
        var captures = 0
        var picks = 0
        var permissions = 0
        compose.setContent { MaterialTheme {
            HomeScreen({ captures++ }, { picks++ }, true, access, {}, { permissions++ })
        } }
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open PAD overlay").performScrollTo().performClick()
        compose.onNodeWithText("Choose existing screenshot").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, captures)
            assertEquals(1, picks)
            assertEquals(0, permissions)
        }
        compose.onNodeWithText("Manage photo access").performScrollTo().performClick()
        compose.onNodeWithText("Allow full photo access").assertDoesNotExist()
        compose.onNodeWithText("Photo permission settings").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, permissions)
        }
    }

    @Test fun switchCanDisableAnActiveShortcutAndHidePermissionActions() {
        val enabled = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            HomeScreen({}, {}, enabled.value, PhotoAccess.FULL, { enabled.value = it }, {})
        } }
        compose.onNodeWithText("Take a screenshot while the overlay is open to capture and solve your board.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Manage photo access").assertDoesNotExist()
        compose.onNodeWithContentDescription("Screenshot shortcut").performScrollTo().performClick()
        compose.onNodeWithText("Use Capture in the overlay, or choose a saved screenshot.").assertIsDisplayed()
        compose.onNodeWithText("Manage photo access").assertDoesNotExist()
    }
}
