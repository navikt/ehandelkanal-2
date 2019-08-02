package no.nav.ehandel.kanal

import com.ibm.mq.jms.MQXAConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import javax.jms.ConnectionFactory
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter

val mqConnectionFactory: ConnectionFactory = UserCredentialsConnectionFactoryAdapter().apply {
    setTargetConnectionFactory(MQXAConnectionFactory().apply {
        hostName = MqProps.hostName
        port = MqProps.port
        channel = MqProps.channelName
        queueManager = MqProps.name
        transportType = WMQConstants.WMQ_CM_CLIENT
        ccsid = WMQConstants.CCSID_UTF8
        setIntProperty(WMQConstants.JMS_IBM_ENCODING, WMQConstants.WMQ_ENCODING_NATIVE)
        setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, WMQConstants.CCSID_UTF8)
    })
    setUsername(MqProps.username)
    setPassword(MqProps.password)
}
