/**
 * XML Parsing library for the key-value store
 *
 * @author Mosharaf Chowdhury (http://www.mosharaf.com)
 * @author Prashanth Mohan (http://www.cs.berkeley.edu/~prmohan)
 *
 * Copyright (c) 2012, University of California at Berkeley
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of University of California, Berkeley nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.berkeley.cs162;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This is the object that is used to generate messages the XML based messages
 * for communication between clients and servers.
 */
public class KVMessage implements Serializable {

    public static final long serialVersionUID = 6473128480951955693L;

    public String msgType = null;
    public String key = null;
    public String value = null;
    public String message = null;
    public String tpcOpId = null;

    /*
     * HINT: You may need to use this for constructors dealing with sockets:
     * http://weblogs.java.net/blog/kohsuke/archive/2005/07/socket_xml_pitf.html
     */
    private class NoCloseInputStream extends FilterInputStream {

        public NoCloseInputStream(InputStream in) {
            super(in);
        }

        public void close() {} // do nothing on close
    }

    private class NoCloseOutputStream extends FilterOutputStream {
        public NoCloseOutputStream(OutputStream os) {
            super(os);
        }

        @Override
        public void close() {}
    }

    public KVMessage(KVMessage kvm) {
        msgType = kvm.msgType;
        key = kvm.key;
        value = kvm.value;
        message = kvm.message;
        tpcOpId = kvm.tpcOpId;
    }

    boolean validMsgType(String msgType) {
        if ("ignoreNext".equals(msgType) || "ready".equals(msgType)
                || "abort".equals(msgType) || "ack".equals(msgType)
                || "getreq".equals(msgType) || "putreq".equals(msgType)
                || "delreq".equals(msgType) || "resp".equals(msgType)
                || "register".equals(msgType) || "commit".equals(msgType)) {
    	    return true;
    	}
    	return false;
    }

    public KVMessage(String msgType) throws KVException {
    	if (validMsgType(msgType)) {
            this.msgType = msgType;
        } else {
            throw new KVException(new KVMessage("resp", "Message format incorrect"));
        }
    }

    public KVMessage(String msgType, String message) throws KVException {
    	if (validMsgType(msgType)) {
            this.msgType = msgType;
            this.message = message;
        } else {
            throw new KVException(new KVMessage("resp", "Message format incorrect"));
        }
    }

    private String getFieldFromKVMessage(Element kvMessageElement, String field) throws KVException {
        NodeList fieldsList = kvMessageElement.getElementsByTagName(field);
        if(fieldsList.getLength() != 1) {
            throw new KVException(new KVMessage("resp", "XML Error: Received unparseable message"));
        }
        Node fieldNode = fieldsList.item(0);
        return fieldNode.getTextContent();
    }

    /**
     * Creates a KVMessage from a socket.
     *
     * @param sock Socket to receive from
     * @throws KVException if there is an error in parsing the message.
     */
    public KVMessage(Socket sock) throws KVException {
    	this(sock, 0);
    }

    /**
     * Creates a KVMessage from a socket within a certain timeout.
     *
     * @param sock Socket to receive from
     * @param timeout millseconds after which you give up
     * @throws KVException if there is an error in parsing the message.
     */
    public KVMessage(Socket sock, int timeout) throws KVException {
		try {
			sock.setSoTimeout(timeout);
            Document parsedSoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new NoCloseInputStream(sock.getInputStream()));
            parsedSoc.getDocumentElement().normalize();
            NodeList nList = parsedSoc.getElementsByTagName("KVMessage");
            Element nNode = (Element) nList.item(0);
            msgType = nNode.getAttribute("type");
            if ("getreq".equals(msgType)) {
                this.key = getFieldFromKVMessage(nNode, "Key");
            } else if ("putreq".equals(msgType)) {
                this.key = getFieldFromKVMessage(nNode, "Key");
                this.value = getFieldFromKVMessage(nNode, "Value");
                if(nNode.getElementsByTagName("TPCOpId").getLength() == 1) {
                    this.tpcOpId = getFieldFromKVMessage(nNode, "TPCOpId");
                }
            } else if ("delreq".equals(msgType)) {
                this.key = getFieldFromKVMessage(nNode, "Key");
                if(nNode.getElementsByTagName("TPCOpId").getLength() == 1) {
                    this.tpcOpId = getFieldFromKVMessage(nNode, "TPCOpId");
                }
            } else if ("resp".equals(msgType) || "register".equals(msgType)) {
                if (nNode.getElementsByTagName("Key").getLength() == 1 && nNode.getElementsByTagName("Value").getLength() == 1) {
                    this.key = getFieldFromKVMessage(nNode, "Key");
                    this.value = getFieldFromKVMessage(nNode, "Value");
                } else {
                    this.message = getFieldFromKVMessage(nNode, "Message");
                }
            } else if ("ready".equals(msgType) || "commit".equals(msgType) || "ack".equals(msgType)) {
                this.tpcOpId = getFieldFromKVMessage(nNode, "TPCOpId");
            } else if ("abort".equals(msgType)) {
                this.tpcOpId = getFieldFromKVMessage(nNode, "TPCOpId");
                if(nNode.getElementsByTagName("Message").getLength() == 1) {
                    this.message = getFieldFromKVMessage(nNode, "Message");
                }
            } else {
                throw new KVException(new KVMessage("resp", "Unknwon Error: msgType is unknown"));
            }
            if (this.key != null && this.key.length() > 256) {
                throw new KVException(new KVMessage("resp", "Oversized key"));
            }
            if (this.value != null && this.value.length() > (256 * 1024)) {
                throw new KVException(new KVMessage("resp", "Oversized value"));
            }
        } catch (SAXException e) {
            throw new KVException(new KVMessage("resp", "XML Error: Received unparseable message"));
        } catch (IOException e) {
            e.printStackTrace();
            throw new KVException(new KVMessage("resp", "Network Error: Could not receive data"));
        } catch (ParserConfigurationException e) {
            throw new KVException(new KVMessage("resp", "XML Error: Received unparseable message"));
		}
    }

	public final String getKey() {
        return key;
    }

    public final void setKey(String key) {
        this.key = key;
    }

    public final String getValue() {
        return value;
    }

    public final void setValue(String value) {
        this.value = value;
    }

    public final String getMessage() {
        return message;
    }

    public final void setMessage(String message) {
        this.message = message;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getTpcOpId() {
        return tpcOpId;
    }

    public void setTpcOpId(String tpcOpId) {
        this.tpcOpId = tpcOpId;
    }

    /**
     * Generate the serialized XML representation for this message.
     *
     * @return the XML String
     * @throws KVException
     */
    public String toXML() throws KVException {
        if (this.key != null && this.key.length() > 256) {
			throw new KVException(new KVMessage("resp", "Oversized key"));
		}
    	if (this.value != null && this.value.length() > (256 * 1024)) {
			throw new KVException(new KVMessage("resp", "Oversized value"));
		}
    	if(!validMsgType(this.msgType)) {
    		throw new KVException(new KVMessage("resp", "Unknwon Error: msgType is unknown"));
    	}

    	try {
    		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    		DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
    		Document doc = dBuilder.newDocument();

    		Element xml = doc.createElement("KVMessage");
	    	xml.setAttribute("type", msgType);
    		doc.appendChild(xml);

	    	if ("getreq".equals(msgType)) {
	    	    Element keyElem = doc.createElement("Key");
	    	    keyElem.appendChild(doc.createTextNode(this.key));
	    		xml.appendChild(keyElem);
	    	} else if ("delreq".equals(msgType)) {
	    	    Element keyElem = doc.createElement("Key");
	    	    keyElem.appendChild(doc.createTextNode(this.key));
	    		xml.appendChild(keyElem);
	    		if(this.tpcOpId != null) {
    	    		Element tpcElem = doc.createElement("TPCOpId");
    	    	    tpcElem.appendChild(doc.createTextNode(this.tpcOpId));
    	    		xml.appendChild(tpcElem);
	    		}
	    	} else if ("putreq".equals(msgType)) {
	    	    Element keyElem = doc.createElement("Key");
	    	    keyElem.appendChild(doc.createTextNode(this.key));
	    		xml.appendChild(keyElem);
	    	    Element valueElem = doc.createElement("Value");
	    	    valueElem.appendChild(doc.createTextNode(this.value));
	    		xml.appendChild(valueElem);
	    		if(this.tpcOpId != null) {
    	    		Element tpcElem = doc.createElement("TPCOpId");
    	    	    tpcElem.appendChild(doc.createTextNode(this.tpcOpId));
    	    		xml.appendChild(tpcElem);
	    		}
            } else if ("ready".equals(msgType) || "commit".equals(msgType) || "ack".equals(msgType)) {
            	Element tpcElem = doc.createElement("TPCOpId");
	    	    tpcElem.appendChild(doc.createTextNode(this.tpcOpId));
	    		xml.appendChild(tpcElem);
            } else if ("abort".equals(msgType)) {
    		    Element tpcElem = doc.createElement("TPCOpId");
	    	    tpcElem.appendChild(doc.createTextNode(this.tpcOpId));
	    		xml.appendChild(tpcElem);
	    		if (this.message != null) {
	    			Element msgElem = doc.createElement("Message");
	    		    msgElem.appendChild(doc.createTextNode(this.message));
	    		    xml.appendChild(msgElem);
	    		}
	    	} else {
	    		if (this.message != null) {
	    		    Element msgElem = doc.createElement("Message");
	    		    msgElem.appendChild(doc.createTextNode(this.message));
	    		    xml.appendChild(msgElem);
	    		}
	    		if (this.key != null && this.value != null) {
    	    	    Element keyElem = doc.createElement("Key");
    	    	    keyElem.appendChild(doc.createTextNode(this.key));
    	    		xml.appendChild(keyElem);
    	    	    Element valueElem = doc.createElement("Value");
    	    	    valueElem.appendChild(doc.createTextNode(this.value));
    	    		xml.appendChild(valueElem);
	    		}
	    	}
            Transformer laBeouf = TransformerFactory.newInstance().newTransformer();
            StringWriter strWriter = new StringWriter();
            StreamResult streamResult = new StreamResult(strWriter);
            laBeouf.transform(new DOMSource(doc), streamResult);
            return strWriter.toString();
	    } catch (ParserConfigurationException e) {
	    	throw new KVException(new KVMessage("resp", "XML Error: Received unparseable message"));
		} catch (TransformerException e) {
	    	throw new KVException(new KVMessage("resp", "XML Error: Received unparseable message"));
        }
    }

    /**
     * Send this message to another host via socket. You will need to
     * flush the stream by calling sock.shutdownOutput()
     *
     * @param sock Socket with which to send this message
     * @throws KVException
     */
    public void sendMessage(Socket sock) throws KVException {
    	String xml = toXML();
        try {
        	PrintWriter pw = new PrintWriter(new NoCloseOutputStream(sock.getOutputStream()), true);
        	pw.write(xml);
        	pw.close();
        	sock.shutdownOutput();
        } catch (IOException e) {
        	throw new KVException(new KVMessage("resp", "Network Error: Could not send data"));
        }
    }

}
