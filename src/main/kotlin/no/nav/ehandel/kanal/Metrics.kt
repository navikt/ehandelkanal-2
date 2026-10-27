package no.nav.ehandel.kanal

import io.prometheus.client.Counter
import io.prometheus.client.Summary

object Metrics {
    private const val NAMESPACE = "ehandelkanal_2"
    private const val LABEL_MESSAGE_TYPE = "message_type"

    val messagesSuccessful: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("messages_successful")
        .help("Number of successful messages")
        .register()

    val messagesFailed: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("messages_failed")
        .help("Number of failed messages")
        .register()

    val messagesReceivedTotal: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("messages_total")
        .help("Total number of messages received")
        .register()

    val messagesReceived: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("event_counter")
        .help("Number of messages received by type")
        .labelNames(LABEL_MESSAGE_TYPE)
        .register()

    val messageSize: Summary = Summary.build()
        .namespace(NAMESPACE)
        .name("message_size_bytes")
        .help("Request size in bytes")
        .register()

    val exhaustedDeliveriesLegalarchive: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("exhausted_deliveries_legalarchive")
        .help("Number of exhausted deliveries to legal archive (file likely too large)")
        .register()

    val exhaustedDeliveriesErrorHandler: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("exhausted_deliveries_errorhandler")
        .help("Number of exhausted deliveries in error handler (to manuellBehandling)")
        .register()

    val exhaustedDeliveriesReport: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("exhausted_deliveries_report")
        .help("Number of exhausted deliveries to report database")
        .register()

    val xmlParsingErrorsCounter: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("xml_parsing_errors")
        .help("Number of messages that could not be JAXB unmarshalled")
        .register()

    val downloadsFailed: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("downloads_failed")
        .help("Number of failed payload downloads from the access point")
        .register()

    val downloadsDeferred: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("downloads_deferred")
        .help("Number of downloads skipped because the message is in backoff")
        .register()

    val downloadAlertThresholdReached: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("download_alert_threshold_reached")
        .help("Number of messages that reached the consecutive download failure alert threshold")
        .register()

    val downloadStateErrors: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("download_state_errors")
        .help("Number of errors reading or writing download retry state")
        .register()
}
