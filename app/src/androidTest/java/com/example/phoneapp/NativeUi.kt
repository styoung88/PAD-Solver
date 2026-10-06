package com.example.phoneapp

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.Searchable
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.io.ByteArrayOutputStream
import java.io.File

/** Query fresh accessibility data across app, overlay, and permission window transitions. */
fun UiDevice.awaitNativeObject(selector: BySelector, timeout: Long): UiObject2? {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val result = wait(object : SearchCondition<UiObject2>() {
        override fun apply(searchable: Searchable): UiObject2? {
            if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            return searchable.findObject(selector)
        }
    }, timeout)
    if (result == null) {
        val automation = instrumentation.uiAutomation
        Log.e("NativeUi", "Missing $selector; screenOn=$isScreenOn; windows=${automation.windows}; root=${automation.rootInActiveWindow}")
        runCatching {
            val hierarchy = ByteArrayOutputStream().also { dumpWindowHierarchy(it) }.toString("UTF-8")
            hierarchy.chunked(3000).forEach { Log.e("NativeUi", it) }
        }.onFailure { Log.e("NativeUi", "Cannot dump hierarchy", it) }
        // CI opts in and retains the APK until these files have been pulled.
        if (InstrumentationRegistry.getArguments().getString("nativeUiDiagnostics") == "true") {
            runCatching {
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "native-ui-failures").apply { mkdirs() }
                val name = System.currentTimeMillis().toString()
                takeScreenshot(File(directory, "$name.png"))
                dumpWindowHierarchy(File(directory, "$name.xml"))
            }.onFailure { Log.e("NativeUi", "Cannot save failure screenshot", it) }
        }
    }
    return result
}
