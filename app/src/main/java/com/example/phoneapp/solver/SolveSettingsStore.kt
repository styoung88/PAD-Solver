package com.example.phoneapp.solver

import android.content.Context
import android.content.SharedPreferences
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.game.GameVariant

/** Keeps independent game profiles in the existing, backed-up preferences file. */
internal class SolveSettingsStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    fun load(game: GameVariant = GameVariant.fromName(preferences.all[GAME_KEY])): SolveSettings {
        migrateLegacySettings()
        return readSettings(preferences.all, game, profilePrefix(game))
    }

    private fun readSettings(values: Map<String, *>, game: GameVariant, prefix: String = "") = SolveSettings(
        game = game,
        preferredColor = OrbColor.attackingColors.firstOrNull { it.name == values[prefix + PREFERRED_COLOR_KEY] },
        targetComboCount = (values[prefix + TARGET_COMBO_COUNT_KEY] as? Int)?.takeIf { it > 0 },
        matchPatternFocus = MatchPattern.entries.firstOrNull { it.name == values[prefix + MATCH_PATTERN_KEY] },
        orbMovement = OrbMovement.entries.firstOrNull { it.name == values[prefix + ORB_MOVEMENT_KEY] } ?: OrbMovement.MEDIUM,
        searchComplexity = SearchComplexity.entries.firstOrNull { it.name == values[prefix + SEARCH_COMPLEXITY_KEY] } ?: SearchComplexity.MEDIUM,
        padZeroTeamSkill = PadZeroTeamSkill.entries.firstOrNull { it.name == values[prefix + PAD_ZERO_TEAM_SKILL_KEY] }
            ?: PadZeroTeamSkill.NONE,
        requireDamageReduction = values[prefix + PAD_ZERO_DAMAGE_REDUCTION_KEY] as? Boolean ?: false,
        teamAttackingAttributes = (values[prefix + PAD_ZERO_ATTRIBUTES_KEY] as? Set<*>)?.let { names ->
            val colors = names.map { name -> OrbColor.attackingColors.firstOrNull { it.name == name } }
            if (colors.all { it != null }) colors.filterNotNull().toSet() else null
        } ?: OrbColor.attackingColors.toSet(),
    )

    fun save(settings: SolveSettings) {
        migrateLegacySettings()
        preferences.edit().apply {
            writeSettings(settings)
            putString(GAME_KEY, settings.game.name)
        }.apply()
    }

    private fun SharedPreferences.Editor.writeSettings(settings: SolveSettings) {
        val prefix = profilePrefix(settings.game)
        if (settings.preferredColor == null) remove(prefix + PREFERRED_COLOR_KEY)
        else putString(prefix + PREFERRED_COLOR_KEY, settings.preferredColor.name)
        if (settings.targetComboCount == null) remove(prefix + TARGET_COMBO_COUNT_KEY)
        else putInt(prefix + TARGET_COMBO_COUNT_KEY, settings.targetComboCount)
        if (settings.matchPatternFocus == null) remove(prefix + MATCH_PATTERN_KEY)
        else putString(prefix + MATCH_PATTERN_KEY, settings.matchPatternFocus.name)
        putString(prefix + ORB_MOVEMENT_KEY, settings.orbMovement.name)
        putString(prefix + SEARCH_COMPLEXITY_KEY, settings.searchComplexity.name)
        putString(prefix + PAD_ZERO_TEAM_SKILL_KEY, settings.padZeroTeamSkill.name)
        putBoolean(prefix + PAD_ZERO_DAMAGE_REDUCTION_KEY, settings.requireDamageReduction)
        putStringSet(prefix + PAD_ZERO_ATTRIBUTES_KEY, settings.teamAttackingAttributes.map { it.name }.toSet())
    }

    private fun migrateLegacySettings() = synchronized(preferences) {
        val values = preferences.all
        if (values[PROFILES_VERSION_KEY] == 1) return@synchronized
        preferences.edit().apply {
            for (game in GameVariant.entries) writeSettings(readSettings(values, game))
            for (key in LEGACY_SETTING_KEYS) remove(key)
            putInt(PROFILES_VERSION_KEY, 1)
        }.apply()
    }

    private fun profilePrefix(game: GameVariant) = "${game.name}."

    /** The returned function unregisters the strongly retained listener. */
    fun observeGameChanges(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == GAME_KEY || key == null) onChanged()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val PREFERENCES_NAME = "solver_preferences"
        const val GAME_KEY = "game_variant"
        const val PROFILES_VERSION_KEY = "game_profiles_version"
        const val PREFERRED_COLOR_KEY = "preferred_orb_color"
        const val TARGET_COMBO_COUNT_KEY = "target_combo_count"
        const val MATCH_PATTERN_KEY = "match_pattern"
        const val ORB_MOVEMENT_KEY = "orb_movement"
        const val SEARCH_COMPLEXITY_KEY = "search_complexity"
        const val LEGACY_BOARD_SIZE_KEY = "board_size_override"
        const val PAD_ZERO_TEAM_SKILL_KEY = "pad_zero_team_skill"
        const val PAD_ZERO_DAMAGE_REDUCTION_KEY = "pad_zero_require_damage_reduction"
        const val PAD_ZERO_ATTRIBUTES_KEY = "pad_zero_attacking_attributes"
        val LEGACY_SETTING_KEYS = listOf(PREFERRED_COLOR_KEY, TARGET_COMBO_COUNT_KEY, MATCH_PATTERN_KEY,
            ORB_MOVEMENT_KEY, SEARCH_COMPLEXITY_KEY, PAD_ZERO_TEAM_SKILL_KEY, PAD_ZERO_DAMAGE_REDUCTION_KEY,
            PAD_ZERO_ATTRIBUTES_KEY, LEGACY_BOARD_SIZE_KEY)
    }
}
