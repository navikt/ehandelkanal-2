package no.nav.ehandel.kanal

import org.apache.camel.CamelContext
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Enkel test som verifiserer at splitting-logikken i AccessPoint-ruten fungerer.
 * Denne testen kjører isolert og tester kun splitting-delen av ruten.
 */
class AccessPointInboxSplitTest {

    private lateinit var camelContext: CamelContext
    private val threadPool = Executors.newFixedThreadPool(6)

    @Before
    fun setUp() {
        camelContext = DefaultCamelContext()
    }

    @After
    fun tearDown() {
        camelContext.stop()
    }

    /**
     * Test at .tokenizeXML("message") IKKE kan splitte JSON.
     * Denne testen skal FEILE med nåværende implementasjon!
     *
     * tokenizeXML prøver å parse JSON som XML, og finner ingen <message> tags,
     * så INGEN meldinger blir splittet ut. mockResult.expectedMessageCount(1) vil feile.
     */
    @Test
    fun `jsonpath should split JSON response `() {
        // Arrange: Opprett en route som bruker SAMME splitting som AccessPoint.kt linje 44
        camelContext.addRoutes(object : RouteBuilder() {
            override fun configure() {
                from("direct:test")
                    .split()
                        .jsonpath("$.meldinger[*]")   // <-- EKSAKT SAMME SOM ACCESSPOINT.KT (dette feiler med JSON!)
                        .streaming()
                        .executorService(threadPool)
                    .to("mock:result")
            }
        })
        camelContext.start()

        val mockResult = camelContext.getEndpoint("mock:result", MockEndpoint::class.java)
        mockResult.expectedMessageCount(1) // Forventer 1 melding, men vil IKKE få det!

        // Act: Send JSON-responsen fra filen (samme som du vil få fra Access Point)
        val jsonBody = this::class.java.getResource("/__files/json/inbox-hent-uleste-meldinger.json")?.readText()
            ?: throw IllegalStateException("Could not read test file")

        camelContext.createProducerTemplate().sendBody("direct:test", jsonBody)

        // Assert: Dette vil FEILE fordi tokenizeXML ikke finner noen <message> tags i JSON!
        // Når testen feiler, vet du at du må endre til .jsonpath()
        mockResult.assertIsSatisfied()
    }
}



