package no.nav.ehandel.kanal

import io.ktor.http.HttpStatusCode
import no.nav.ehandel.kanal.common.singletons.objectMapper
import no.nav.ehandel.kanal.routes.HttpErrorResponse
import org.amshove.kluent.shouldBeEqualTo
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Test

class ObjectMapperTest {

    @Test
    fun `error response with all fields`() {
        val response = HttpErrorResponse(
            url = "http://localhost/report",
            message = "Invalid input",
            cause = "java.lang.IllegalArgumentException: boom",
            code = HttpStatusCode.BadRequest,
            callId = "abc-123"
        )
        val expected = """
            {"url":"http://localhost/report","message":"Invalid input",
             "cause":"java.lang.IllegalArgumentException: boom",
             "code":{"value":400,"description":"Bad Request"},"callId":"abc-123"}
        """
        objectMapper.readTree(objectMapper.writeValueAsString(response)) shouldBeEqualTo objectMapper.readTree(expected)
    }

    @Test
    fun `error response omits null fields`() {
        val response = HttpErrorResponse(url = "http://localhost/report")
        val expected = """{"url":"http://localhost/report","code":{"value":500,"description":"Internal Server Error"}}"""
        objectMapper.readTree(objectMapper.writeValueAsString(response)) shouldBeEqualTo objectMapper.readTree(expected)
    }

    @Test
    fun `joda datetime is serialized`() {
        val dateTime = DateTime(2026, 9, 29, 12, 0, DateTimeZone.UTC)
        objectMapper.writeValueAsString(dateTime) shouldBeEqualTo "1790683200000"
    }

    @Test
    fun `readTree reads nested fields`() {
        val json = """{"id":"42","access_token":"t","expires_in":3599}"""
        val tree = objectMapper.readTree(json)
        tree.get("id").asText() shouldBeEqualTo "42"
        tree.get("expires_in").asInt() shouldBeEqualTo 3599
    }
}
