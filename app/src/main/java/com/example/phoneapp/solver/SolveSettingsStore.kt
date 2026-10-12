package com.example.phoneapp.solver

import android.content.Context
import android.content.SharedPreferences
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.game.GameVariant

/** Retains existing preference keys so installed apps keep their solve settings. */
internal class SolveSettingsStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    fun load(): SolveSettings {
        val values = preferences.all
        return SolveSettings(
            game = GameVariant.fromName(values[GAME_KEY]),
            preferredColor = OrbColor.attackingColors.firstOrNull { it.name == values[PREFERRED_COLOR_KEY] },
            targetComboCount = (values[TARGET_COMBO_COUNT_KEY] as? Int)?.takeIf { it > 0 },
            matchPatternFocus = MatchPattern.entries.firstOrNull { it.name == values[MATCH_PATTERN_KEY] },
            orbMovement = OrbMovement.entries.firstOrNull { it.name == values[ORB_MOVEMENT_KEY] } ?: OrbMovement.MEDIUM,
            searchComplexity = SearchComplexity.entries.firstOrNull { it.name == values[SEARCH_COMPLEXITY_KEY] } ?: SearchComplexity.MEDIUM,
            padZeroTeamSkill = PadZeroTeamSkill.entries.firstOrNull { it.name == values[PAD_ZERO_TEAM_SKILL_KEY] }
                ?: PadZeroTeamSkill.NONE,
            requireDamageReduction = values[PAD_ZERO_DAMAGE_REDUCTION_KEY] as? Boolean ?: false,
            teamAttackingAttributes = (values[PAD_ZERO_ATTRIBUTES_KEY] as? Set<*>)?.let { names ->
                val colors = names.map { name -> OrbColor.attackingColors.firstOrNull { it.name == name } }
                if (colors.all { it != null }) colors.filterNotNull().toSet() else null
            } ?: OrbColor.attackingColors.toSet(),
        )
    }

    fun save(settings: SolveSettings) {
        preferences.edit().apply {
            putString(GAME_KEY, settings.game.name)
            if (settings.preferredColor == null) remove(PREFERRED_COLOR_KEY)
            else putString(PREFERRED_COLOR_KEY, settings.preferredColor.name)
            if (settings.targetComboCount == null) remove(TARGET_COMBO_COUNT_KEY)
            else putInt(TARGET_COMBO_COUNT_KEY, settings.targetComboCount)
            if (settings.matchPatternFocus == null) remove(MATCH_PATTERN_KEY)
            else putString(MATCH_PATTERN_KEY, settings.matchPatternFocus.name)
            putString(ORB_MOVEMENT_KEY, settings.orbMovement.name)
            putString(SEARCH_COMPLEXITY_KEY, settings.searchComplexity.name)
            putString(PAD_ZERO_TEAM_SKILL_KEY, settings.padZeroTeamSkill.name)
            putBoolean(PAD_ZERO_DAMAGE_REDUCTION_KEY, settings.requireDamageReduction)
            putStringSet(PAD_ZERO_ATTRIBUTES_KEY, settings.teamAttackingAttributes.map { it.name }.toSet())
            remove(LEGACY_BOARD_SIZE_KEY)
        }.apply()
    }

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
        const val PREFERRED_COLOR_KEY = "preferred_orb_color"
        const val TARGET_COMBO_COUNT_KEY = "target_combo_count"
        const val MATCH_PATTERN_KEY = "match_pattern"
        const val ORB_MOVEMENT_KEY = "orb_movement"
        const val SEARCH_COMPLEXITY_KEY = "search_complexity"
        const val LEGACY_BOARD_SIZE_KEY = "board_size_override"
        const val PAD_ZERO_TEAM_SKILL_KEY = "pad_zero_team_skill"
        const val PAD_ZERO_DAMAGE_REDUCTION_KEY = "pad_zero_require_damage_reduction"
        const val PAD_ZERO_ATTRIBUTES_KEY = "pad_zero_attacking_attributes"
    }
}
