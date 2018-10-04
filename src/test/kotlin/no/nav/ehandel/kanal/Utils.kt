package no.nav.ehandel.kanal

import java.io.File

private class Utils

inline fun <reified T> String.getResource(): T {
    return when (T::class.java) {
        String::class.java -> Utils::class.java.getResource(this).readText() as T
        File::class.java -> File(Utils::class.java.getResource(this).file) as T
        else -> throw Exception("Invalid return type")
    }
}
