package com.example.phoneapp.board

/** Grid sizes supported by the mobile game, in columns × rows. */
enum class BoardSize(val columns: Int, val rows: Int, val maxOrbMoves: Int) {
    SMALL(5, 4, 30),
    STANDARD(6, 5, 40),
    LARGE(7, 6, 50);

    val cellCount: Int get() = columns * rows
    val displayName: String get() = "$columns×$rows"

    companion object {
        fun of(columns: Int, rows: Int): BoardSize? = entries.firstOrNull {
            it.columns == columns && it.rows == rows
        }
    }
}
