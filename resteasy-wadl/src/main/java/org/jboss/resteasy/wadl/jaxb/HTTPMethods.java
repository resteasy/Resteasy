//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.06.18 at 10:10:02 PM CST 
//


package org.jboss.resteasy.wadl.jaxb;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HTTPMethods.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HTTPMethods"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}NMTOKEN"&gt;
 *     &lt;enumeration value="GET"/&gt;
 *     &lt;enumeration value="POST"/&gt;
 *     &lt;enumeration value="PUT"/&gt;
 *     &lt;enumeration value="HEAD"/&gt;
 *     &lt;enumeration value="DELETE"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HTTPMethods")
@XmlEnum
public enum HTTPMethods {

   GET,
   POST,
   PUT,
   HEAD,
   DELETE;

   public String value() {
      return name();
   }

   public static HTTPMethods fromValue(String v) {
      return valueOf(v);
   }

}
