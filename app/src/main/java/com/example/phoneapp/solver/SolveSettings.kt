package com.example.phoneapp.solver

import com.example.phoneapp.board.OrbColor

/** User-selected objectives shared by the app and the automatic overlay solver. */
data class SolveSettings(
    val preferredColor: OrbColor? = null,
    val targetComboCount: Int? = null,
    val matchPatternFocus: MatchPattern? = null,
) {
    init {
        require(preferredColor == null || preferredColor.isAttacking)
        require(targetComboCount == null || targetComboCount > 0)
    }

    fun toGoal(): SolveGoal = ConfiguredGoal(preferredColor, buildList {
        targetComboCount?.let { add(ComboMinimum(it)) }
        matchPatternFocus?.let { add(RequiredPattern(it, preferredColor)) }
    })
}
