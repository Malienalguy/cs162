/**
 * Implementation of a set-associative cache.
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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

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


/**
 * A set-associate cache which has a fixed maximum number of sets (numSets).
 * Each set has a maximum number of elements (MAX_ELEMS_PER_SET).
 * If a set is full and another entry is added, an entry is dropped based on the eviction policy.
 */
public class KVCache implements KeyValueInterface {
    private int numSets = 100;
    private int maxElemsPerSet = 10;

    private final ArrayList<LinkedList<CacheNode>> caches;
    private final WriteLock[] locks;

    /**
     * Creates a new LRU cache.
     * @param cacheSize    the maximum number of entries that will be kept in this cache.
     */
    public KVCache(int numSets, int maxElemsPerSet) {
        this.numSets = numSets;
        this.maxElemsPerSet = maxElemsPerSet;
        caches = new ArrayList<LinkedList<CacheNode>>(numSets);
        locks = new WriteLock[numSets];
        for(int i = 0; i < numSets; i++) {
            caches.add(new LinkedList<CacheNode>());
            // We can use a regular java object but for some reason they
            // want to use WriteLocks
            locks[i] = new ReentrantReadWriteLock().writeLock();
        }
    }

    /**
     * Retrieves an entry from the cache.
     * Assumes the corresponding set has already been locked for writing.
     * @param key the key whose associated value is to be returned.
     * @return the value associated to this key, or null if no value with this key exists in the cache.
     */
    @Override
    public String get(String key) {
        // Must be called before anything else
        AutoGrader.agCacheGetStarted(key);
        AutoGrader.agCacheGetDelay();

        int setId = getSetId(key);
        for(CacheNode node : caches.get(setId)) {
            if(node.key.equals(key)) {
                node.referenced = true;

                // Must be called before returning
                AutoGrader.agCacheGetFinished(key);

                return node.value;
            }
        }

        // Must be called before returning
        AutoGrader.agCacheGetFinished(key);
        return null;
    }

    /**
     * Adds an entry to this cache.
     * If an entry with the specified key already exists in the cache, it is replaced by the new entry.
     * If the cache is full, an entry is removed from the cache based on the eviction policy
     * Assumes the corresponding set has already been locked for writing.
     * @param key    the key with which the specified value is to be associated.
     * @param value    a value to be associated with the specified key.
     * @return true is something has been overwritten
     */
    @Override
    public void put(String key, String value) {
        // Must be called before anything else
        AutoGrader.agCachePutStarted(key, value);
        AutoGrader.agCachePutDelay();

        int setId = getSetId(key);
        // Make sure our element isn't already there and update if it is
        for(CacheNode node : caches.get(setId)) {
            if(node.key.equals(key)) {
                node.value = value;
                node.referenced = true;

                // Must be called before returning
                AutoGrader.agCachePutFinished(key, value);
                return;
            }
        }

        if(caches.get(setId).size() == maxElemsPerSet) {
            // We must proceed with second chance.
            CacheNode oldest = caches.get(setId).poll();
            while(oldest.referenced) {
                oldest.referenced = false; // Second chance
                caches.get(setId).offer(oldest);
                oldest = caches.get(setId).poll();
            }
        }
        // We've just dropped the oldest from the cache
        // Or there was still room
        caches.get(setId).offer(new CacheNode(key, value, false));

        // Must be called before returning
        AutoGrader.agCachePutFinished(key, value);
    }

    /**
     * Removes an entry from this cache.
     * Assumes the corresponding set has already been locked for writing.
     * @param key    the key with which the specified value is to be associated.
     */
    @Override
    public void del (String key) {
        // Must be called before anything else
        AutoGrader.agCacheDelStarted(key);
        AutoGrader.agCacheDelDelay();

        int setId = getSetId(key);
        for(int i = 0; i < caches.get(setId).size(); i++) {
            CacheNode node = caches.get(setId).get(i);
            if(node.key.equals(key)) {
                caches.get(setId).remove(i);
            }
        }

        // Must be called before returning
        AutoGrader.agCacheDelFinished(key);
    }

    /**
     * @param key
     * @return    the write lock of the set that contains key.
     */
    public WriteLock getWriteLock(String key) {
        return locks[getSetId(key)];
    }

    /**
     *
     * @param key
     * @return    set of the key
     */
    private int getSetId(String key) {
        return Math.abs(key.hashCode()) % numSets;
    }

    public String toXML() {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element rootElem = doc.createElement("KVCache");
            doc.appendChild(rootElem);

            for (int i = 0; i < caches.size(); i++) {
                LinkedList<CacheNode> cache = caches.get(i);
                Element setElem = doc.createElement("Set");
                setElem.setAttribute("Id", Integer.toString(i));
                for(CacheNode node : cache) {
                    Element cElem = doc.createElement("CacheEntry");
                    cElem.setAttribute("isReferenced", Boolean.toString(node.referenced));
                    cElem.setAttribute("isValid", "true");

                    Element kElem = doc.createElement("Key");
                    kElem.appendChild(doc.createTextNode(node.key));
                    Element vElem = doc.createElement("Value");
                    vElem.appendChild(doc.createTextNode(node.value));

                    cElem.appendChild(kElem);
                    cElem.appendChild(vElem);
                    setElem.appendChild(cElem);
                }
                rootElem.appendChild(setElem);

                if(cache.size() < maxElemsPerSet) {
                    // Fill it with invalid entries
                    for(int k = 0; k < (maxElemsPerSet - cache.size()); k++) {
                        Element cElem = doc.createElement("CacheEntry");
                        cElem.setAttribute("isReferenced", "false");
                        cElem.setAttribute("isValid", "false");

                        Element kElem = doc.createElement("Key");
                        kElem.appendChild(doc.createTextNode("null"));
                        Element vElem = doc.createElement("Value");
                        vElem.appendChild(doc.createTextNode("null"));

                        cElem.appendChild(kElem);
                        cElem.appendChild(vElem);
                        setElem.appendChild(cElem);
                    }
                }
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

    private class CacheNode {
        public String key;
        public String value;
        public boolean referenced;

        public CacheNode(String key, String value, boolean referenced) {
            this.key = key;
            this.value = value;
            this.referenced = referenced;
        }
    }
}
