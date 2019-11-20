package no.nav.ehandel.kanal.common.extensions

import kotlin.math.ln
import kotlin.math.pow

fun Long.humanReadableByteCount(): String {
    val unit = 1000
    if (this < unit) return "$this B"
    val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = "kMGTPE"[exp - 1]
    return String.format("%.1f %sB", this / unit.toDouble().pow(exp.toDouble()), pre)
}
