package no.nav.ehandel.kanal.common.models

import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false) {
    fun fail() {
        this.running = false
        this.initialized = false
        logger.warn { "Application self tests failing - expect imminent shutdown" }
    }
}
