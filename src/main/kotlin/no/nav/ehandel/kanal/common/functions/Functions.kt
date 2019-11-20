package no.nav.ehandel.kanal.common.functions

import java.util.UUID
import kotlin.reflect.KClass
import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.ehandel.kanal.common.constants.MDC_CALL_ID
import org.slf4j.MDC

private val logger = KotlinLogging.logger { }

internal fun randomUuid() = UUID.randomUUID().toString()
internal fun getCorrelationId(): String =
    MDC.get(MDC_CALL_ID)

internal suspend fun <T> retry(
    callName: String,
    attempts: Int = 5,
    initialDelay: Long = 250L,
    maxDelay: Long = 2000L,
    vararg illegalExceptions: KClass<out Throwable> = arrayOf(),
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(attempts - 1) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            if (illegalExceptions.any { it.isInstance(e) }) {
                throw e
            }
            logger.warn(e) { "$callName: Attempt ${attempt + 1} of $attempts failed - retrying in $currentDelay ms" }
        }
        delay(currentDelay)
        currentDelay = (currentDelay * 2.0).toLong().coerceAtMost(maxDelay)
    }
    return try {
        block()
    } catch (e: Throwable) {
        logger.warn(e) { "$callName: Final retry attempt failed" }
        throw e
    }
}
