package com.example.phoneapp

import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.solver.SolveSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class BackupRulesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun legacyBackupIncludesOnlyTheActualSolvePreferencesFile() {
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertEquals(R.xml.backup_rules, manifestRule("fullBackupContent"))
        assertAllowlist(R.xml.backup_rules, setOf("full-backup-content"))
    }

    @Test fun modernCloudAndTransferRulesExcludeCapturesAndDeviceSpecificState() {
        assertEquals(R.xml.data_extraction_rules, manifestRule("dataExtractionRules"))
        assertAllowlist(R.xml.data_extraction_rules, setOf("cloud-backup", "device-transfer", "cross-platform-transfer"))
    }

    private fun manifestRule(attribute: String): Int {
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { xml ->
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "application") {
                    return xml.getAttributeResourceValue("http://schemas.android.com/apk/res/android", attribute, 0)
                }
                xml.next()
            }
        }
        error("Application missing from packaged manifest")
    }

    private fun assertAllowlist(resource: Int, expectedScopes: Set<String>) {
        var preferenceName = ""
        SolveSettingsStore(object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(name, mode).also { preferenceName = name }
        })
        val includes = mutableMapOf<String, MutableList<Pair<String, String>>>()
        var scope = ""
        context.resources.getXml(resource).use { xml ->
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG) {
                    if (xml.name in expectedScopes) {
                        scope = xml.name
                        includes[scope] = mutableListOf()
                    } else if (xml.name == "include") {
                        includes.getValue(scope).add(xml.getAttributeValue(null, "domain") to xml.getAttributeValue(null, "path"))
                    }
                }
                xml.next()
            }
        }
        assertEquals(expectedScopes, includes.keys)
        // A single exact include disables default inclusion of captures, other preferences, and future files.
        for (paths in includes.values) assertEquals(listOf("sharedpref" to "$preferenceName.xml"), paths)
    }
}
