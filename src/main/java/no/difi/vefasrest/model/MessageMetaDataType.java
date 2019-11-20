
package no.difi.vefasrest.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for message-meta-dataType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="message-meta-dataType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="msg-no" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="accountId" type="{}accountId" minOccurs="0"/>
 *         &lt;element name="direction" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="received" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="uuid" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="peppol-header" type="{}peppol-headerType"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "message-meta-dataType", propOrder = {
    "msgNo",
    "accountId",
    "direction",
    "received",
    "uuid",
    "peppolHeader"
})
public class MessageMetaDataType {

    @XmlElement(name = "msg-no", required = true)
    protected String msgNo;
    protected AccountId accountId;
    @XmlElement(required = true)
    protected String direction;
    @XmlElement(required = true)
    protected String received;
    @XmlElement(required = true)
    protected String uuid;
    @XmlElement(name = "peppol-header", required = true)
    protected PeppolHeaderType peppolHeader;

    /**
     * Gets the value of the msgNo property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMsgNo() {
        return msgNo;
    }

    /**
     * Sets the value of the msgNo property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMsgNo(String value) {
        this.msgNo = value;
    }

    /**
     * Gets the value of the accountId property.
     * 
     * @return
     *     possible object is
     *     {@link AccountId }
     *     
     */
    public AccountId getAccountId() {
        return accountId;
    }

    /**
     * Sets the value of the accountId property.
     * 
     * @param value
     *     allowed object is
     *     {@link AccountId }
     *     
     */
    public void setAccountId(AccountId value) {
        this.accountId = value;
    }

    /**
     * Gets the value of the direction property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDirection() {
        return direction;
    }

    /**
     * Sets the value of the direction property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDirection(String value) {
        this.direction = value;
    }

    /**
     * Gets the value of the received property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReceived() {
        return received;
    }

    /**
     * Sets the value of the received property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReceived(String value) {
        this.received = value;
    }

    /**
     * Gets the value of the uuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets the value of the uuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUuid(String value) {
        this.uuid = value;
    }

    /**
     * Gets the value of the peppolHeader property.
     * 
     * @return
     *     possible object is
     *     {@link PeppolHeaderType }
     *     
     */
    public PeppolHeaderType getPeppolHeader() {
        return peppolHeader;
    }

    /**
     * Sets the value of the peppolHeader property.
     * 
     * @param value
     *     allowed object is
     *     {@link PeppolHeaderType }
     *     
     */
    public void setPeppolHeader(PeppolHeaderType value) {
        this.peppolHeader = value;
    }

}
