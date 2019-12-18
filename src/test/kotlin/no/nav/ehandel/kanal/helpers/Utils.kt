package no.nav.ehandel.kanal.helpers

import java.io.File
import org.amshove.kluent.shouldBeEqualTo

inline fun <reified T> String.getResource(): T = when (T::class.java) {
    String::class.java -> object {}.javaClass.getResource(this).readText() as T
    File::class.java -> File(object {}.javaClass.getResource(this).file) as T
    else -> throw Exception("Invalid return type")
}

infix fun String.shouldBeXmlEqualTo(other: String) = this.stripCharacters() shouldBeEqualTo other.stripCharacters()
private fun String.stripCharacters() = this.replace("[\n\r\\s+]".toRegex(), "")
