package com.example.phoneapp.game

enum class GameVariant(val displayName: String, val packageName: String) {
    PAD("PAD", "jp.gungho.padEN"),
    PAD_ZERO("PAD 0", "jp.gungho.hangerss");

    val recognitionUnavailableMessage: String get() = "$displayName board recognition is not available yet."

    companion object {
        fun fromName(value: Any?): GameVariant = entries.firstOrNull { it.name == value } ?: PAD
    }
}
