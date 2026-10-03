package com.example.phoneapp.board

enum class OrbColor {
    FIRE,
    WATER,
    WOOD,
    LIGHT,
    DARK,
    HEART,
    UNKNOWN,
}

/** Properties that can be added without multiplying the set of orb colors. */
enum class OrbModifier {
    LOCKED,
    ENHANCED,
}

data class Orb(
    val color: OrbColor,
    val modifiers: Set<OrbModifier> = emptySet(),
)

data class BoardPosition(val row: Int, val column: Int)

data class Board(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    val orbs: List<Orb>,
) {
    init {
        require(columns > 0 && rows > 0) { "Board dimensions must be positive." }
        require(orbs.size == columns * rows) {
            "Expected ${columns * rows} orbs, received ${orbs.size}."
        }
    }

    operator fun get(position: BoardPosition): Orb = orbs[indexOf(position)]

    fun indexOf(position: BoardPosition): Int {
        require(position.row in 0 until rows && position.column in 0 until columns)
        return position.row * columns + position.column
    }

    fun positionOf(index: Int): BoardPosition {
        require(index in orbs.indices)
        return BoardPosition(row = index / columns, column = index % columns)
    }

    companion object {
        const val DEFAULT_COLUMNS = 6
        const val DEFAULT_ROWS = 5
    }
}
