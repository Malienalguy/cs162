/**
 * Persistent Key-Value storage layer. Current implementation is transient,
 * but assume to be backed on disk when you do your project.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
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
 * This is a dummy KeyValue Store. Ideally this would go to disk, or some other
 * backing store. For this project, we simulate the disk like system using a
 * manual delay.
 *
 *
 *
 */
public class KVStore implements KeyValueInterface {
    private Map<String, String> store = null;

    public KVStore() {
        resetStore();
    }

    private void resetStore() {
        store = new HashMap<String, String>();
    }

    @Override
    public void put(String key, String value) throws KVException {
        AutoGrader.agStorePutStarted(key, value);

        try {
            putDelay();
            store.put(key, value);
        } finally {
            AutoGrader.agStorePutFinished(key, value);
        }
    }

    @Override
    public String get(String key) throws KVException {
        AutoGrader.agStoreGetStarted(key);

        try {
            getDelay();
            String retVal = this.store.get(key);
            if (retVal == null) {
                KVMessage msg = new KVMessage("resp", "Does not exist");
                throw new KVException(msg);
            }
            return retVal;
        } finally {
            AutoGrader.agStoreGetFinished(key);
        }
    }

    @Override
    public void del(String key) throws KVException {
        AutoGrader.agStoreDelStarted(key);

        try {
            delDelay();
            if (key != null) {
                if(this.store.containsKey(key)) {
                    this.store.remove(key);
                } else {
                    KVMessage msg = new KVMessage("resp", "Does not exist");
                    throw new KVException(msg);
                }
            }
        } finally {
            AutoGrader.agStoreDelFinished(key);
        }
    }

    private void getDelay() {
        AutoGrader.agStoreDelay();
    }

    private void putDelay() {
        AutoGrader.agStoreDelay();
    }

    private void delDelay() {
        AutoGrader.agStoreDelay();
    }

    public String toXML() {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element rootElem = doc.createElement("KVStore");
            doc.appendChild(rootElem);

            for (Map.Entry<String, String> entry : this.store.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                Element cElem = doc.createElement("KVPair");

                Element kElem = doc.createElement("Key");
                kElem.appendChild(doc.createTextNode(key));
                Element vElem = doc.createElement("Value");
                vElem.appendChild(doc.createTextNode(value));

                cElem.appendChild(kElem);

                cElem.appendChild(vElem);

                rootElem.appendChild(cElem);

            }
            Transformer laBeouf = TransformerFactory.newInstance().newTransformer();
            StringWriter strWriter = new StringWriter();
            StreamResult streamResult = new StreamResult(strWriter);
            laBeouf.transform(new DOMSource(doc), streamResult);
            return strWriter.toString();
        } catch (ParserConfigurationException e) { // Potentially thrown by the
                                                   // newDocumentBuilder() call
            e.printStackTrace(System.out);
            return null;
        } catch (FactoryConfigurationError f) { // Caused by the
                                                // DocumentBuilderFactory.newInstance()
                                                // call
            f.printStackTrace(System.out);
            return null;
        } catch (TransformerException e) {
            e.printStackTrace(System.out);
            return null;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            return null;
        }
    }

    public void dumpToFile(String fileName) {
        try {
            FileWriter file_writer = new FileWriter(fileName); // Constructs a
                                                               // FileWriter
                                                               // object given a
                                                               // file name
            BufferedWriter buffered_writer = new BufferedWriter(file_writer);

            String str = this.toXML();

            buffered_writer.write(str); // Potentially throws an IOExecption
            buffered_writer.close(); // Closes the stream, flushing it first.
        } catch (IOException e) { // From either BufferedWriter.write() or the
                                  // FileWriter constructor
            e.printStackTrace(System.out);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    /**
     * Replaces the contents of the store with the contents of a file written by
     * dumpToFile; the previous contents of the store are lost.
     *
     * @param fileName
     *            the file to be read.
     */
    public void restoreFromFile(String fileName) {
        // Reset it
        resetStore();

        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            File file = new File(fileName);
            // Check if file exists and is readable

            Document d = builder.parse(file);
            // Normalize text representation
            d.getDocumentElement().normalize();

            NodeList pairList = d.getElementsByTagName("KVPair");

            for (int i = 0; i < pairList.getLength(); i++) {
                Element node = (Element) pairList.item(i);
                Node keyNode = node.getElementsByTagName("Key").item(0);
                Node valueNode = node.getElementsByTagName("Value").item(0);
                store.put(keyNode.getTextContent(), valueNode.getTextContent());
            }
        } catch (FactoryConfigurationError f) { // Caused by the
                                                // DocumentBuilderFactory.newInstance()
                                                // call
            f.printStackTrace(System.out);
        } catch (IOException i) { // Caused by the DocumentBuilder.parse() call
            i.printStackTrace(System.out);
        } catch (SAXException s) { // Caused by the DocumentBuilder.parse() call
            s.printStackTrace(System.out);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
}
