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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScreenshotShortcutScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepScreenAwake() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test fun selectedAccessExplainsTheLimitationButKeepsBothManualActionsAvailable() {
        showLimitedAccess(PhotoAccess.SELECTED, "Paused: selected photos do not include new screenshots. Allow all photos, or use Capture.")
    }

    @Test fun deniedAccessExplainsTheLimitationButKeepsBothManualActionsAvailable() {
        showLimitedAccess(PhotoAccess.NONE, "Paused: full photo access is needed to detect new screenshots. You can still use Capture.")
    }

    private fun showLimitedAccess(access: PhotoAccess, message: String) {
        var captures = 0
        var picks = 0
        var permissions = 0
        var settings = 0
        compose.setContent { MaterialTheme {
            HomeScreen({ captures++ }, { picks++ }, true, access, {}, { permissions++ }, { settings++ })
        } }
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open PAD overlay").performScrollTo().performClick()
        compose.onNodeWithText("Choose existing screenshot").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, captures)
            assertEquals(1, picks)
            assertEquals(0, permissions)
        }
        compose.onNodeWithText("Allow full photo access").performScrollTo().performClick()
        compose.onNodeWithText("Photo permission settings").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, permissions)
            assertEquals(1, settings)
        }
    }

    @Test fun switchCanDisableAnActiveShortcutAndHidePermissionActions() {
        val enabled = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            HomeScreen({}, {}, enabled.value, PhotoAccess.FULL, { enabled.value = it }, {}, {})
        } }
        compose.onNodeWithText("Active while the overlay is open: taking a phone screenshot captures and solves the board.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Screenshot shortcut").performScrollTo().performClick()
        compose.onNodeWithText("Off. Use Capture in the overlay or choose a screenshot.").assertIsDisplayed()
        compose.onNodeWithText("Allow full photo access").assertDoesNotExist()
        compose.onNodeWithText("Photo permission settings").assertDoesNotExist()
    }
}
