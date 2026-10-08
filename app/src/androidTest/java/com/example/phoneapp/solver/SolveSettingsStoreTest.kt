package com.example.phoneapp.solver

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.OrbColor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SolveSettingsStoreTest {
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("solve_settings_store_test", Context.MODE_PRIVATE)

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @After
    fun cleanUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun readsExistingPreferenceKeysIncludingNonPresetPositiveTargets() {
        preferences.edit()
            .putString("preferred_orb_color", "WOOD")
            .putInt("target_combo_count", 6)
            .putString("match_pattern", "TPA")
            .commit()
        assertEquals(SolveSettings(OrbColor.WOOD, 6, MatchPattern.TPA), SolveSettingsStore(preferences).load())
    }

    @Test
    fun invalidNamesColorsTargetsAndTypesFallBackToDefaults() {
        for (color in listOf("OLD_COLOR", "HEART", "UNKNOWN")) {
            preferences.edit()
                .putString("preferred_orb_color", color)
                .putInt("target_combo_count", -1)
                .putString("match_pattern", "OLD_PATTERN")
                .commit()
            assertEquals(SolveSettings(), SolveSettingsStore(preferences).load())
        }
        preferences.edit()
            .putInt("preferred_orb_color", 1)
            .putString("target_combo_count", "5")
            .putBoolean("match_pattern", true)
            .commit()
        assertEquals(SolveSettings(), SolveSettingsStore(preferences).load())
    }

    @Test
    fun separateReadersSeeSavedSettingsAndClearedOptions() {
        val writer = SolveSettingsStore(preferences)
        val reader = SolveSettingsStore(preferences)
        val settings = SolveSettings(OrbColor.FIRE, 7, MatchPattern.CROSS)
        writer.save(settings)
        assertEquals(settings, reader.load())
        preferences.edit().putBoolean("unrelated", true).commit()
        writer.save(SolveSettings())
        assertEquals(SolveSettings(), reader.load())
        assertFalse(preferences.contains("preferred_orb_color"))
        assertFalse(preferences.contains("target_combo_count"))
        assertFalse(preferences.contains("match_pattern"))
        assertFalse(preferences.contains("board_size_override"))
        assertEquals(true, preferences.getBoolean("unrelated", false))
    }

    @Test
    fun ignoresAndClearsLegacyBoardSizeWithoutLosingGoals() {
        preferences.edit().putString("board_size_override", "LARGE").putInt("target_combo_count", 14).commit()
        val store = SolveSettingsStore(preferences)
        val settings = SolveSettings(targetComboCount = 14)
        assertEquals(settings, store.load())
        store.save(settings)
        assertFalse(preferences.contains("board_size_override"))
        assertEquals(settings, store.load())
    }

    @Test
    fun searchLevelsPersistAndInvalidValuesUseMedium() {
        val store = SolveSettingsStore(preferences)
        for (movement in OrbMovement.entries) {
            for (complexity in SearchComplexity.entries) {
                val settings = SolveSettings(orbMovement = movement, searchComplexity = complexity)
                store.save(settings)
                assertEquals(settings, SolveSettingsStore(preferences).load())
            }
        }
        preferences.edit().putInt("orb_movement", 1).putString("search_complexity", "OLD").commit()
        assertEquals(SolveSettings(), store.load())
    }
}
