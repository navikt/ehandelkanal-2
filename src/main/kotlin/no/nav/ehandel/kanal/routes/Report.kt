package no.nav.ehandel.kanal.routes

import io.ktor.application.call
import io.ktor.html.respondHtml
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.title
import kotlinx.html.ul
import no.nav.ehandel.kanal.formatDate
import no.nav.ehandel.kanal.report.CsvValues
import no.nav.ehandel.kanal.report.Report
import org.joda.time.DateTime

fun Routing.report() {
    route("/reports") {
        get {
            val distinctDates: List<DateTime> = Report.getAllUniqueDaysWithEntries()
            call.respondHtml {
                head { title { +"Liste over rapporter" } }
                body {
                    h1 {
                        +"Liste over rapporter"
                    }
                    ul {
                        distinctDates.forEach { value ->
                            val date = value.formatDate()
                            li {
                                a(href = "/reports/$date") {
                                    +"$date.csv"
                                }
                            }
                        }
                    }
                }
            }
        }

        get("/{fileName}") {
            val fileName = call.parameters["fileName"] ?: throw IllegalArgumentException("Missing parameter 'fileName'")
            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "$fileName.csv").toString())
            call.respondBytes(contentType = ContentType.Text.CSV, status = HttpStatusCode.OK, bytes = Report.getAllAsCsvFile(DateTime.parse(fileName)))
        }
        post {
            val value: CsvValues = call.receive()
            call.respond(HttpStatusCode.OK, Report.insert(value))
        }
    }
}
