/**
 * Log for Two-Phase Commit
 *
 * @author Mosharaf Chowdhury (http://www.mosharaf.com)
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
 *  DISCLAIMED. IN NO EVENT SHALL PRASHANTH MOHAN BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.berkeley.cs162;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

public class TPCLog {

    // Path to log file
    public String logPath = null;
    // Reference to the KVServer of this slave. Populated by rebuildKeyServer()
    public KVServer kvServer = null;

    // Log entries
    public ArrayList<KVMessage> entries = null;

    /*
     * Keeps track of the interrupted 2PC operation if there is one. There can
     * be at most one, ie., when the last 2PC operation before crashing was in
     * READY state. This should be set during a call to rebuildKeyServer()
     * during recovery.
     */
    public KVMessage interruptedTpcOperation = null;

    /**
     *
     * @param logPath
     * @param kvServer
     *            Reference to the KVServer of this slave. Populated by
     *            rebuildKeyServer() during start.
     */
    public TPCLog(String logPath, KVServer kvServer) {
        this.logPath = logPath;
        entries = new ArrayList<KVMessage>();
        this.kvServer = kvServer;
    }

    public ArrayList<KVMessage> getEntries() {
        return entries;
    }

    public boolean empty() {
        return entries.size() == 0;
    }

    /**
     * Add an entry to the log and flush the entire log to disk. You do not have
     * to efficiently append entries onto the log stored on disk.
     *
     * @param entry
     *            KVMessage to write to the log
     */
    public void appendAndFlush(KVMessage entry) {
        System.out.println("appending: " + entry.getMsgType());
        if ("putreq".equals(entry.getMsgType())
                || "delreq".equals(entry.getMsgType())
                || "ready".equals(entry.getMsgType())
                || "abort".equals(entry.getMsgType())
                || "commit".equals(entry.getMsgType())) {
            entries.add(entry);
            flushToDisk();
        }
    }

    /**
     * Load log from persistent storage.
     */
    @SuppressWarnings("unchecked")
    public void loadFromDisk() {
        ObjectInputStream inputStream = null;

        try {
            inputStream = new ObjectInputStream(new FileInputStream(logPath));
            entries = (ArrayList<KVMessage>) inputStream.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // If log never existed, there are no entries
            if (entries == null) {
                entries = new ArrayList<KVMessage>();
            }

            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes the log to persistent storage.
     */
    public void flushToDisk() {
        ObjectOutputStream outputStream = null;

        try {
            outputStream = new ObjectOutputStream(new FileOutputStream(logPath));
            outputStream.writeObject(entries);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Load log and rebuild KVServer by iterating over log entries. You do not
     * need to restore the previous cache state (ie. ignore GETS). Set
     * interruptedTpcOperation, if there is one (ie., slave crashed in the READY
     * state).
     *
     * @throws KVException
     *             if an error occurs in KVServer (we do not expect any)
     */
    public void rebuildKeyServer() throws KVException {
        loadFromDisk();
        KVMessage prev = null;
        for (KVMessage entry : entries) {
            System.out.println("build: " + entry.getMsgType());
            if (entry.getMsgType().equals("commit") && prev != null) {
                if (prev.getMsgType().equals("putreq")) {
                    kvServer.put(prev.getKey(), prev.getValue());
                } else if (prev.getMsgType().equals("delreq")) {
                    kvServer.del(prev.getKey());
                }
            } else if(entry.getMsgType().equals("putreq") || entry.getMsgType().equals("delreq")) {
                prev = entry;
            }
        }
        interruptedTpcOperation = entries.get(entries.size() - 1);
    }

    /**
     *
     * @return Interrupted 2PC operation, if any
     */
    public KVMessage getInterruptedTpcOperation() {
        KVMessage logEntry = interruptedTpcOperation;
        interruptedTpcOperation = null;
        return logEntry;
    }

    /**
     *
     * @return True if TPCLog contains an interrupted 2PC operation
     */
    public boolean hasInterruptedTpcOperation() {
        return interruptedTpcOperation != null;
    }
}
