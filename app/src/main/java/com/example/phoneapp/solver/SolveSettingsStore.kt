package com.example.phoneapp.solver

import android.content.Context
import android.content.SharedPreferences
import com.example.phoneapp.board.OrbColor

/** Retains existing preference keys so installed apps keep their solve settings. */
internal class SolveSettingsStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    fun load(): SolveSettings {
        val values = preferences.all
        return SolveSettings(
            preferredColor = OrbColor.attackingColors.firstOrNull { it.name == values[PREFERRED_COLOR_KEY] },
            targetComboCount = (values[TARGET_COMBO_COUNT_KEY] as? Int)?.takeIf { it > 0 },
            matchPatternFocus = MatchPattern.entries.firstOrNull { it.name == values[MATCH_PATTERN_KEY] },
            orbMovement = OrbMovement.entries.firstOrNull { it.name == values[ORB_MOVEMENT_KEY] } ?: OrbMovement.MEDIUM,
            searchComplexity = SearchComplexity.entries.firstOrNull { it.name == values[SEARCH_COMPLEXITY_KEY] } ?: SearchComplexity.MEDIUM,
        )
    }

    fun save(settings: SolveSettings) {
        preferences.edit().apply {
            if (settings.preferredColor == null) remove(PREFERRED_COLOR_KEY)
            else putString(PREFERRED_COLOR_KEY, settings.preferredColor.name)
            if (settings.targetComboCount == null) remove(TARGET_COMBO_COUNT_KEY)
            else putInt(TARGET_COMBO_COUNT_KEY, settings.targetComboCount)
            if (settings.matchPatternFocus == null) remove(MATCH_PATTERN_KEY)
            else putString(MATCH_PATTERN_KEY, settings.matchPatternFocus.name)
            putString(ORB_MOVEMENT_KEY, settings.orbMovement.name)
            putString(SEARCH_COMPLEXITY_KEY, settings.searchComplexity.name)
            remove(LEGACY_BOARD_SIZE_KEY)
        }.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "solver_preferences"
        const val PREFERRED_COLOR_KEY = "preferred_orb_color"
        const val TARGET_COMBO_COUNT_KEY = "target_combo_count"
        const val MATCH_PATTERN_KEY = "match_pattern"
        const val ORB_MOVEMENT_KEY = "orb_movement"
        const val SEARCH_COMPLEXITY_KEY = "search_complexity"
        const val LEGACY_BOARD_SIZE_KEY = "board_size_override"
    }
}
