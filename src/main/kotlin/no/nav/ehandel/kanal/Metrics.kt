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
}
