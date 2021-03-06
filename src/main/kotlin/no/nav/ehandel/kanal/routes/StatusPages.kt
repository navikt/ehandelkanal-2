package no.nav.ehandel.kanal.routes

import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.features.ResponseException
import io.ktor.client.response.readText
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.ehandel.kanal.common.extensions.url
import no.nav.ehandel.kanal.common.functions.getCorrelationId

private val logger = KotlinLogging.logger { }

fun StatusPages.Configuration.exceptionHandler() {
    exception<Throwable> { cause ->
        call.logErrorAndRespond(cause) { "An internal error occurred during routing" }
    }
    exception<IllegalArgumentException> { cause ->
        call.logErrorAndRespond(cause, HttpStatusCode.BadRequest) { "Invalid input" }
    }
    exception<ResponseException> { cause ->
        logger.error(cause) {
            runBlocking { cause.response.readText() }
        }
        call.logErrorAndRespond(cause) { "An external service responded with a HTTP error" }
    }
}

fun StatusPages.Configuration.notFoundHandler() {
    status(HttpStatusCode.NotFound) {
        call.respond(
            HttpStatusCode.NotFound,
            HttpErrorResponse(
                message = "The page or operation requested does not exist.",
                code = HttpStatusCode.NotFound,
                url = call.request.url()
            )
        )
    }
}

private suspend inline fun ApplicationCall.logErrorAndRespond(
    cause: Throwable,
    status: HttpStatusCode = HttpStatusCode.InternalServerError,
    lazyMessage: () -> String
) {
    val message = lazyMessage()
    logger.error(cause) { message }
    val response = HttpErrorResponse(
        url = this.request.url(),
        cause = cause.toString(),
        message = message,
        code = status,
        callId = getCorrelationId()
    )
    logger.error { "Status Page Response: $response" }
    this.respond(status, response)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class HttpErrorResponse(
    val url: String,
    val message: String? = null,
    val cause: String? = null,
    val code: HttpStatusCode = HttpStatusCode.InternalServerError,
    val callId: String? = null
)
