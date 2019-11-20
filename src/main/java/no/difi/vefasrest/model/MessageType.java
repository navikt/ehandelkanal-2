
package no.difi.vefasrest.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for messageType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="messageType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="self" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="xml-document" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="message-meta-data" type="{}message-meta-dataType"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "messageType", propOrder = {
    "self",
    "xmlDocument",
    "messageMetaData"
})
public class MessageType {

    @XmlElement(required = true)
    protected String self;
    @XmlElement(name = "xml-document", required = true)
    protected String xmlDocument;
    @XmlElement(name = "message-meta-data", required = true)
    protected MessageMetaDataType messageMetaData;

    /**
     * Gets the value of the self property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSelf() {
        return self;
    }

    /**
     * Sets the value of the self property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSelf(String value) {
        this.self = value;
    }

    /**
     * Gets the value of the xmlDocument property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getXmlDocument() {
        return xmlDocument;
    }

    /**
     * Sets the value of the xmlDocument property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setXmlDocument(String value) {
        this.xmlDocument = value;
    }

    /**
     * Gets the value of the messageMetaData property.
     * 
     * @return
     *     possible object is
     *     {@link MessageMetaDataType }
     *     
     */
    public MessageMetaDataType getMessageMetaData() {
        return messageMetaData;
    }

    /**
     * Sets the value of the messageMetaData property.
     * 
     * @param value
     *     allowed object is
     *     {@link MessageMetaDataType }
     *     
     */
    public void setMessageMetaData(MessageMetaDataType value) {
        this.messageMetaData = value;
    }

}
