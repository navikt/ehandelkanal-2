package no.nav.ehandel.kanal

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.Charset
import java.util.Base64
import no.nav.ehandel.kanal.services.legalarchive.ArchiveRequest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class ArchiveRequestTest {
    val mapper = jacksonObjectMapper()
    val encoder = Base64.getEncoder()

    val id = "1"
    val sender = "sender"
    val receiver = "receiver"
    val joarkRef = "ref"
    val retention = 10
    val content = "test".toByteArray()
    val contentEncoded = encoder.encode(content).toString(Charset.forName("UTF-8"))

    @Test
    fun `request with required parameters only`() {
        val request = ArchiveRequest(messageId = id, messageContent = content, sender = sender, receiver = receiver)
        val expected =
            """{"meldingsId":"$id","avsender":"$sender","mottaker":"$receiver","meldingsInnhold":"$contentEncoded"}"""
        mapper.writeValueAsString(request) shouldBeEqualTo expected
    }

    @Test
    fun `request with required parameters and joark reference`() {
        val request = ArchiveRequest(
            messageId = id,
            messageContent = content,
            sender = sender,
            receiver = receiver,
            joarkReference = joarkRef
        )
        val expected =
            """{"meldingsId":"$id","avsender":"$sender","mottaker":"$receiver","meldingsInnhold":"$contentEncoded","joarkRef":"$joarkRef"}"""
        mapper.writeValueAsString(request) shouldBeEqualTo expected
    }

    @Test
    fun `request with required parameters and retention`() {
        val request = ArchiveRequest(
            messageId = id,
            messageContent = content,
            sender = sender,
            receiver = receiver,
            retentionInYears = retention
        )
        val expected =
            """{"meldingsId":"$id","avsender":"$sender","mottaker":"$receiver","meldingsInnhold":"$contentEncoded","antallAarLagres":$retention}"""
        mapper.writeValueAsString(request) shouldBeEqualTo expected
    }

    @Test
    fun `request with all parameters`() {
        val request = ArchiveRequest(
            messageId = id,
            messageContent = content,
            sender = sender,
            receiver = receiver,
            joarkReference = joarkRef,
            retentionInYears = retention
        )
        val expected =
            """{"meldingsId":"$id","avsender":"$sender","mottaker":"$receiver","meldingsInnhold":"$contentEncoded","joarkRef":"$joarkRef","antallAarLagres":$retention}"""
        mapper.writeValueAsString(request) shouldBeEqualTo expected
    }
}
