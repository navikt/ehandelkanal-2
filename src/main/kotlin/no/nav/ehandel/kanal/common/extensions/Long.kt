package no.nav.ehandel.kanal.common.extensions

import kotlin.math.abs

fun Long.humanReadableByteCount(): String {
    val s = if (this < 0) "-" else ""
    var b = if (this == Long.MIN_VALUE) Long.MAX_VALUE else abs(this)
    return when {
        b < 1000L -> "$this B"
        b < 999950L -> String.format("%s%.1f kB", s, b / 1e3)
        1000.let { b /= it; b } < 999950L -> String.format("%s%.1f MB", s, b / 1e3)
        1000.let { b /= it; b } < 999950L -> String.format("%s%.1f GB", s, b / 1e3)
        1000.let { b /= it; b } < 999950L -> String.format("%s%.1f TB", s, b / 1e3)
        1000.let { b /= it; b } < 999950L -> String.format("%s%.1f PB", s, b / 1e3)
        else -> String.format("%s%.1f EB", s, b / 1e6)
    }
}
