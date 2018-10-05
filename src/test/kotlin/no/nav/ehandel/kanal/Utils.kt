package no.nav.ehandel.kanal

import org.amshove.kluent.shouldBeEqualTo
import java.io.File

private class Utils

inline fun <reified T> String.getResource(): T = when (T::class.java) {
    String::class.java -> Utils::class.java.getResource(this).readText() as T
    File::class.java -> File(Utils::class.java.getResource(this).file) as T
    else -> throw Exception("Invalid return type")
}

infix fun String.shouldBeXmlEqualTo(other: String) = this.stripCharacters() shouldBeEqualTo other.stripCharacters()
private fun String.stripCharacters() = this.replace("[\n\r\\s+]".toRegex(), "")
