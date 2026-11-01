package dev.remotegpt.app.ui

import androidx.compose.runtime.*

internal enum class TerminalModifier { OFF, ONCE, LOCKED;
    val active get() = this != OFF
    fun tap() = if (this == OFF) ONCE else OFF
    fun lock() = if (this == LOCKED) OFF else LOCKED
    fun consume() = if (this == ONCE) OFF else this
}

/** Each terminal gets its own latches. Reading a modifier never consumes it. */
@Stable internal class TerminalModifiers {
    var ctrl by mutableStateOf(TerminalModifier.OFF)
    var alt by mutableStateOf(TerminalModifier.OFF)
    var shift by mutableStateOf(TerminalModifier.OFF)
    fun consume() { ctrl = ctrl.consume(); alt = alt.consume(); shift = shift.consume() }
}
