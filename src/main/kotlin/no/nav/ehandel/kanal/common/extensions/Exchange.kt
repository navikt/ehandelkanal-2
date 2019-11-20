package no.nav.ehandel.kanal.common.extensions

import org.apache.camel.Exchange

inline fun <reified T> Exchange.getBody(): T = getIn().getBody(T::class.java)
inline fun <reified T> Exchange.getHeader(name: String): T = getIn().getHeader(name, T::class.java)
