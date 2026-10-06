package com.example.phoneapp.capture

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.regex.Pattern

class PhotoPermissionSystemTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val probePackage = instrumentation.context.packageName
    private val device = UiDevice.getInstance(instrumentation)
    private val timeout = 10_000L
    private val allow = By.res(Pattern.compile(".*:id/permission_allow(_all)?_button"))

    @Before fun resetProbePermissions() {
        // Permission mutations are restricted to the separate test APK, never the installed app.
        check(probePackage == "${target.packageName}.test")
        for (permission in PhotoAccess.requestedPermissions()) {
            shell("pm clear-permission-flags $probePackage $permission user-set user-fixed")
            shell("pm revoke $probePackage $permission")
        }
    }

    @After fun cleanUp() {
        device.pressBack()
        resetProbePermissions()
    }

    @Test fun freshRequestOffersTheNativeFullAccessChoice() {
        launchProbe()
        requestAccess()
        assertNotNull("A fresh request must offer Android's allow choice", device.wait(Until.findObject(allow), timeout))
        if (Build.VERSION.SDK_INT >= 34) {
            assertNotNull("Android 14+ must also offer selected access", device.findObject(
                By.res(Pattern.compile(".*:id/permission_allow_selected_button")),
            ))
        }
        device.pressBack()
    }

    @Test fun blockedFullAccessCannotBeRestoredByRepeatingTheNativeRequest() {
        if (Build.VERSION.SDK_INT >= 34) {
            shell("pm grant $probePackage ${Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}")
        }
        for (permission in PhotoAccess.requestedPermissions()) {
            val flags = if (Build.VERSION.SDK_INT >= 34) "user-set" else "user-set user-fixed"
            shell("pm set-permission-flags $probePackage $permission $flags")
        }
        launchProbe()
        if (Build.VERSION.SDK_INT >= 34) {
            requestAccess()
            val declineMore = awaitObject(By.text(Pattern.compile("(?i)don[’']t (select|allow) more")))
            assertTrue("Before declining more photos, Android still offers full access", device.hasObject(allow))
            // Slow emulator window transitions can drop an injected touch even after the
            // button appears. Invoke the native button's accessibility action instead.
            assertTrue("Android must accept Don't select more", declineMore.accessibilityNodeInfo.performAction(
                AccessibilityNodeInfo.ACTION_CLICK,
            ))
            awaitObject(By.text("Request completed"))
        }
        repeat(2) {
            requestAccess()
            if (Build.VERSION.SDK_INT >= 34) {
                assertTrue("After Don't select more, Android opens photo reselection", device.wait(
                    Until.hasObject(By.pkg(Pattern.compile(".*(photopicker|providers\\.media).*"))), timeout,
                ))
                assertFalse("Reselection must not be mistaken for the original Allow all prompt", device.hasObject(allow))
                device.pressBack()
                assertEquals(PackageManager.PERMISSION_GRANTED, grant(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
            }
            awaitObject(By.text("Request completed"))
            assertEquals(PackageManager.PERMISSION_DENIED, grant(PhotoAccess.requestedPermissions().first()))
            assertFalse(device.hasObject(allow))
        }
    }

    @Test fun settingsLinkOpensTheCorrectAppsInfoWithoutChangingPhotoGrants() {
        val permission = PhotoAccess.requestedPermissions().first()
        val before = grant(permission)
        val settings = PhotoAccess.settingsIntent(probePackage)
        target.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val row = device.wait(Until.findObject(By.text("Permissions")), timeout)
        assertNotNull("App info must expose the Permissions row", row)
        val label = target.packageManager.getApplicationLabel(target.packageManager.getApplicationInfo(probePackage, 0)).toString()
        assertNotNull("App info must show the requested app's label", device.findObject(By.text(label)))
        // The public App info action must address this package, even if an OEM ignores highlighting.
        assertEquals("package:$probePackage", settings.data.toString())
        assertEquals(before, grant(permission))
        assertTrue(device.currentPackageName != target.packageName)
    }

    private fun launchProbe() {
        target.startActivity(Intent().apply {
            component = ComponentName(probePackage, PhotoPermissionProbeActivity::class.java.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("permissions", PhotoAccess.requestedPermissions())
        })
        awaitObject(By.text("Request photo access"))
    }

    private fun requestAccess() { awaitObject(By.text("Request photo access")).click() }

    private fun awaitObject(selector: BySelector): UiObject2 =
        checkNotNull(device.wait(Until.findObject(selector), timeout)) { "Missing system UI: $selector" }

    private fun grant(permission: String) = target.packageManager.checkPermission(permission, probePackage)

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
