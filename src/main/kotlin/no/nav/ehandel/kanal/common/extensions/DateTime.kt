package no.nav.ehandel.kanal.common.extensions

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

fun DateTime.formatDate(): String = DateTimeFormat.forPattern(
    "yyyy-MM-dd"
).print(this)
