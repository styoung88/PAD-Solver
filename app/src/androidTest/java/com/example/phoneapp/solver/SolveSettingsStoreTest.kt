package com.example.phoneapp.solver

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.game.GameVariant
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
        assertEquals(SolveSettings(OrbColor.WOOD, 6, MatchPattern.TPA, game = GameVariant.PAD_ZERO),
            SolveSettingsStore(preferences).load(GameVariant.PAD_ZERO))
        assertFalse(preferences.contains("preferred_orb_color"))
    }

    @Test
    fun invalidNamesColorsTargetsAndTypesFallBackToDefaults() {
        for (color in listOf("OLD_COLOR", "HEART", "UNKNOWN")) {
            preferences.edit().clear()
                .putString("preferred_orb_color", color)
                .putInt("target_combo_count", -1)
                .putString("match_pattern", "OLD_PATTERN")
                .commit()
            assertEquals(SolveSettings(), SolveSettingsStore(preferences).load())
        }
        preferences.edit().clear()
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
        assertFalse(preferences.contains("PAD.preferred_orb_color"))
        assertFalse(preferences.contains("PAD.target_combo_count"))
        assertFalse(preferences.contains("PAD.match_pattern"))
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
    fun gameSelectionDefaultsPersistsAndRejectsInvalidValues() {
        val store = SolveSettingsStore(preferences)
        assertEquals(GameVariant.PAD, store.load().game)
        store.save(SolveSettings(game = GameVariant.PAD_ZERO, targetComboCount = 6))
        assertEquals(SolveSettings(game = GameVariant.PAD_ZERO, targetComboCount = 6),
            SolveSettingsStore(preferences).load())
        for (value in listOf("REMOVED_GAME", "", "PAD")) {
            preferences.edit().putString("game_variant", value).commit()
            assertEquals(GameVariant.PAD, store.load().game)
            assertEquals(null, store.load().targetComboCount)
        }
        preferences.edit().putInt("game_variant", 1).commit()
        assertEquals(GameVariant.PAD, store.load().game)
    }

    @Test
    fun gameObserverIgnoresOtherPreferencesAndCanBeUnregistered() {
        val store = SolveSettingsStore(preferences)
        var changes = 0
        lateinit var stopObserving: () -> Unit
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            stopObserving = store.observeGameChanges { changes++ }
            store.save(SolveSettings(game = GameVariant.PAD_ZERO))
            assertEquals(1, changes)
            store.save(SolveSettings(game = GameVariant.PAD_ZERO, targetComboCount = 5))
            assertEquals(1, changes)
            stopObserving()
            store.save(SolveSettings())
            assertEquals(1, changes)
        }
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
        preferences.edit().putInt("PAD.orb_movement", 1).putString("PAD.search_complexity", "OLD").commit()
        assertEquals(SolveSettings(), store.load())
    }

    @Test fun padZeroTeamSkillsPersistAcrossGameChangesAndSeparateReaders() {
        val store = SolveSettingsStore(preferences)
        for (skill in PadZeroTeamSkill.entries) {
            val settings = SolveSettings(game = GameVariant.PAD_ZERO, padZeroTeamSkill = skill,
                requireDamageReduction = true, teamAttackingAttributes = setOf(OrbColor.FIRE, OrbColor.WOOD))
            store.save(settings)
            assertEquals(settings, SolveSettingsStore(preferences).load())
            store.save(SolveSettings())
            assertEquals(SolveSettings(), store.load())
            assertEquals(settings, SolveSettingsStore(preferences).load(GameVariant.PAD_ZERO))
        }
        val emptyAttributes = SolveSettings(game = GameVariant.PAD_ZERO, teamAttackingAttributes = emptySet())
        store.save(emptyAttributes)
        assertEquals(emptyAttributes, store.load())
    }

    @Test fun corruptTeamSettingsFallBackWithoutAffectingExistingPreferences() {
        preferences.edit().putInt("pad_zero_team_skill", 4)
            .putString("pad_zero_require_damage_reduction", "true")
            .putString("pad_zero_attacking_attributes", "FIRE")
            .putString("preferred_orb_color", "WOOD").commit()
        val expected = SolveSettings(preferredColor = OrbColor.WOOD)
        assertEquals(expected, SolveSettingsStore(preferences).load())
        preferences.edit().putString("PAD.pad_zero_team_skill", "REMOVED")
            .putStringSet("PAD.pad_zero_attacking_attributes", setOf("FIRE", "HEART", "OLD_COLOR")).commit()
        assertEquals(expected, SolveSettingsStore(preferences).load())
    }

    @Test fun gameProfilesSaveClearAndReloadIndependently() {
        val store = SolveSettingsStore(preferences)
        val pad = SolveSettings(OrbColor.WOOD, 6, MatchPattern.CROSS,
            orbMovement = OrbMovement.LOW, searchComplexity = SearchComplexity.HIGH)
        val padZero = SolveSettings(OrbColor.FIRE, 9, MatchPattern.TPA,
            orbMovement = OrbMovement.HIGH, searchComplexity = SearchComplexity.VERY_HIGH,
            game = GameVariant.PAD_ZERO, padZeroTeamSkill = PadZeroTeamSkill.MULTICOLOR,
            requireDamageReduction = true, teamAttackingAttributes = setOf(OrbColor.FIRE, OrbColor.LIGHT))
        store.save(pad)
        store.save(padZero)
        val reader = SolveSettingsStore(preferences)
        assertEquals(padZero, reader.load())
        assertEquals(pad, reader.load(GameVariant.PAD))
        assertEquals(padZero, reader.load())
        store.save(SolveSettings())
        assertEquals(SolveSettings(), reader.load())
        assertEquals(padZero, reader.load(GameVariant.PAD_ZERO))
    }

    @Test fun migrationPreservesTheSelectedGameAndBothProfilesAfterFutureSaves() {
        preferences.edit().putString("game_variant", "PAD_ZERO")
            .putString("preferred_orb_color", "DARK").putInt("target_combo_count", 8)
            .putString("orb_movement", "HIGH").putString("pad_zero_team_skill", "MULTICOLOR")
            .putBoolean("pad_zero_require_damage_reduction", true)
            .putStringSet("pad_zero_attacking_attributes", setOf("FIRE", "WOOD"))
            .putBoolean("unrelated", true).commit()
        val store = SolveSettingsStore(preferences)
        val original = SolveSettings(preferredColor = OrbColor.DARK, targetComboCount = 8,
            orbMovement = OrbMovement.HIGH, game = GameVariant.PAD_ZERO,
            padZeroTeamSkill = PadZeroTeamSkill.MULTICOLOR, requireDamageReduction = true,
            teamAttackingAttributes = setOf(OrbColor.FIRE, OrbColor.WOOD))
        assertEquals(original, store.load())
        assertEquals(original.copy(game = GameVariant.PAD), store.load(GameVariant.PAD))
        store.save(SolveSettings(game = GameVariant.PAD_ZERO))
        assertEquals(original.copy(game = GameVariant.PAD), SolveSettingsStore(preferences).load(GameVariant.PAD))
        assertEquals(true, preferences.getBoolean("unrelated", false))
        assertFalse(preferences.contains("target_combo_count"))
    }
}
