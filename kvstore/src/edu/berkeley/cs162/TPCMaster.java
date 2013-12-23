/**
 * Master for Two-Phase Commits
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import edu.berkeley.cs162.TPCMaster.TPCRegistrationHandler.RegistrationHandler;

public class TPCMaster {

    // Timeout value used during 2PC operations
    public static final int TIMEOUT_MILLISECONDS = 5000;

    // Port on localhost to run registration server on
    private static final int REGISTRATION_PORT = 9090;

    // Cache stored in the Master/Coordinator Server
    public KVCache masterCache = new KVCache(100, 10);

    // Registration server that uses TPCRegistrationHandler
    public SocketServer regServer = null;

    // Number of slave servers in the system
    public int numSlaves = -1;

    // ID of the next 2PC operation
    public Long tpcOpId = 0L;

    private ArrayList<SlaveInfo> keySpace = new ArrayList<SlaveInfo>();

    /**
     * Creates TPCMaster
     *
     * @param numSlaves number of slave servers expected to register
     * @throws Exception
     */
    public TPCMaster(int numSlaves) {
        this.numSlaves = numSlaves;
        try {
            regServer = new SocketServer(InetAddress.getLocalHost().getHostAddress(),
                                         REGISTRATION_PORT);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates tpcOpId to be used for an operation. In this implementation
     * it is a long variable that increases by one for each 2PC operation.
     *
     * @return
     */
    public String getNextTpcOpId() {
        tpcOpId++;
        return tpcOpId.toString();
    }

    /**
     * Start registration server in a separate thread.
     */
    public void run() {
        AutoGrader.agTPCMasterStarted();
        regServer.addHandler(new TPCRegistrationHandler());
        try {
            regServer.connect();
        } catch (IOException e) {
            // We can't do anything in this case because no
            // servers will be able to register so we will
            // rethrow it and force failure.
            throw new RuntimeException(e);
        }
        new RegistrationListenerThread(regServer).start();
        AutoGrader.agTPCMasterFinished();
    }

    private static class RegistrationListenerThread extends Thread {

        private final SocketServer regServer;

        public RegistrationListenerThread(SocketServer regServer) {
            this.regServer = regServer;
        }

        @Override
        public void run() {
            try {
                regServer.run();
            } catch (IOException e) {
                // If we encounter an IOException servicing
                // requests we can no longer service requests
                // and we must fail the application.
                throw new RuntimeException(e);
        	}
        }
    }

    /**
     * Converts Strings to 64-bit longs. Borrowed from http://goo.gl/le1o0W,
     * adapted from String.hashCode().
     *
     * @param string String to hash to 64-bit
     * @return long hashcode
     */
    public long hashTo64bit(String string) {
        long h = 1125899906842597L;
        int len = string.length();

        for (int i = 0; i < len; i++) {
            h = (31 * h) + string.charAt(i);
        }
        return h;
    }

    /**
     * Compares two longs as if they were unsigned (Java doesn't have unsigned
     * data types except for char). Borrowed from http://goo.gl/QyuI0V
     *
     * @param n1 First long
     * @param n2 Second long
     * @return is unsigned n1 less than unsigned n2
     */
    public boolean isLessThanUnsigned(long n1, long n2) {
        return (n1 < n2) ^ ((n1 < 0) != (n2 < 0));
    }

    public boolean isLessThanEqualUnsigned(long n1, long n2) {
        return isLessThanUnsigned(n1, n2) || (n1 == n2);
    }

    /**
     * Find primary replica for a given key.
     *
     * @param key
     * @return SlaveInfo of first replica
     */
    public SlaveInfo findFirstReplica(String key) {
        // 64-bit hash of the key
        long hashedKey = hashTo64bit(key.toString());
        for(SlaveInfo info : keySpace) {
            // Return the first slave that has a higher ID than the hashed key
            if(isLessThanEqualUnsigned(hashedKey, info.getSlaveID())) {
                return info;
            }
        }
        // If it's greater than the last element we give it the first one.
        return keySpace.get(0);
    }

    /**
     * Find the successor of firstReplica.
     *
     * @param firstReplica SlaveInfo of primary replica
     * @return SlaveInfo of successor replica
     */
    public SlaveInfo findSuccessor(SlaveInfo firstReplica) {
        int index = keySpace.indexOf(firstReplica);
        if(index == keySpace.size() - 1) {
            return keySpace.get(0);
        } else {
            return keySpace.get(index + 1);
        }
    }

    /**
     * Synchronized method to perform 2PC operations. This method contains the
     * bulk of the two-phase commit logic. It performs phase 1 and phase 2
     * with appropriate timeouts and retries. See the spec for details on the
     * expected behavior.
     *
     * @param msg
     * @param isPutReq boolean to distinguish put and del requests
     * @throws KVException if the operation cannot be carried out
     */
    public synchronized void performTPCOperation(KVMessage msg, boolean isPutReq) throws KVException {
        AutoGrader.agPerformTPCOperationStarted(isPutReq);

        String key = msg.getKey();
        String value = msg.getValue();
        String opId = getNextTpcOpId();

        synchronized(masterCache.getWriteLock(key)) {
            KVMessage req = new KVMessage(isPutReq ? "putreq" : "delreq");
            req.setKey(key);
            if(isPutReq) {
                req.setValue(value);
            }
            req.setTpcOpId(opId);

            SlaveInfo primary = findFirstReplica(key);
            SlaveInfo secondary = findSuccessor(primary);

            Socket pCon = primary.connectHost();
            Socket sCon = secondary.connectHost();
            boolean abort = false;
            // Send the initial message
            try {
                req.sendMessage(pCon);
            } catch (KVException e) {
                abort = true;
            }
            try {
                req.sendMessage(sCon);
            } catch (KVException e) {
                abort = true;
            }

            // Get the first response
            KVMessage pResp = null;
            KVMessage sResp = null;
            try {
                pResp = new KVMessage(pCon, TIMEOUT_MILLISECONDS);
                sResp = new KVMessage(sCon, TIMEOUT_MILLISECONDS);
            } catch (KVException e) {
                abort = true;
            }
            if(abort || (!"ready".equals(pResp.getMsgType()) || !"ready".equals(sResp.getMsgType()))) {
                // Send abort if we don't recieve anything
                sendDecisionUntilAck(primary, secondary, "abort", opId);
                KVMessage errorMsg = mergeErrorMessages(primary, pResp, secondary, sResp);
                AutoGrader.agPerformTPCOperationFinished(isPutReq);
                throw new KVException(errorMsg);
            } else {
                // Send commit if we've recieved everything
                sendDecisionUntilAck(primary, secondary, "commit", opId);
                // We need to update the cache after our operation completes.
                if(masterCache.get(key) != null) {
                    if(isPutReq) {
                        masterCache.put(key, value);
                    } else {
                        masterCache.del(key);
                	}
                }
            }

            AutoGrader.agPerformTPCOperationFinished(isPutReq);
            return;
        }
    }

    private void sendDecisionUntilAck(SlaveInfo primary, SlaveInfo secondary, String decision, String opId) {
        boolean pAcked = false;
        boolean sAcked = false;
        Socket pCon = null;
        Socket sCon = null;

        // Build decision
        KVMessage commitMsg;
        try {
            commitMsg = new KVMessage(decision);
        } catch (KVException e) {
            // This should never ever happen because we only call this with valid
            // message types.
            throw new RuntimeException(e);
    	}
        commitMsg.setTpcOpId(opId);

        while(!(pAcked && sAcked)) {
            if(!pAcked) {
                try {
                    // Send decision
                    pCon = primary.connectHost();
                    commitMsg.sendMessage(pCon);
                    // Wait for ack
                    KVMessage pAck = new KVMessage(pCon, TIMEOUT_MILLISECONDS);
                    if("ack".equals(pAck.getMsgType())) {
                        pAcked = true;
                    }
                } catch (KVException e) {
                    pAcked = false;
                    e.printStackTrace();
                }
            }
            if(!sAcked) {
                try {
                    // Send decision
                    sCon = secondary.connectHost();
                    commitMsg.sendMessage(sCon);
                    // Wait for ack
                    KVMessage sAck = new KVMessage(sCon, TIMEOUT_MILLISECONDS);
                    if("ack".equals(sAck.getMsgType())) {
                        sAcked = true;
                    }
                } catch (KVException e) {
                    sAcked = false;
                }
            }
        }
    }

    /**
     * Perform GET operation in the following manner:
     * - Try to GET from cache, return immediately if found
     * - Try to GET from first/primary replica
     * - If primary succeeded, return value
     * - If primary failed, try to GET from the other replica
     * - If secondary succeeded, return value
     * - If secondary failed, return KVExceptions from both replicas
     * Please see spec for more details.
     *
     * @param msg Message containing Key to get
     * @return Value corresponding to the Key
     * @throws KVException
     */
    public String handleGet(KVMessage msg) throws KVException {
        AutoGrader.aghandleGetStarted();
        String key = msg.getKey();
        synchronized(masterCache.getWriteLock(key)) {
            String value = masterCache.get(key);
            if(value == null) {
                SlaveInfo primary = findFirstReplica(key);
                SlaveInfo secondary = findSuccessor(primary);
                KVMessage getReq = new KVMessage("getreq");
                getReq.setKey(key);
                Socket pCon = primary.connectHost();
                getReq.sendMessage(pCon);
                KVMessage pResp = new KVMessage(pCon);
                if(pResp.getValue() != null) {
                    value = pResp.getValue();
                } else {
                    Socket sCon = secondary.connectHost();
                    getReq.sendMessage(sCon);
                    KVMessage sResp = new KVMessage(sCon);
                    if(sResp.getValue() != null) {
                        value = sResp.getValue();
                    } else {
                        KVMessage merged = mergeErrorMessages(primary, pResp, secondary, sResp);
                        AutoGrader.aghandleGetFinished();
                        throw new KVException(merged);
                    }
                }
            }
            AutoGrader.aghandleGetFinished();
            return value;
        }
    }

    private KVMessage mergeErrorMessages(SlaveInfo primary, KVMessage pError, SlaveInfo secondary, KVMessage sError) {
        KVMessage toReturn;
        try {
            toReturn = new KVMessage("resp");
        } catch (KVException e) {
            // We should never hit this case because we are using a predefined type.
            throw new RuntimeException(e);
        }
        String message1 = "@" + primary.getSlaveID() + ":=" + pError.getMessage() + "\n";
        String message2 = "@" + secondary.getSlaveID() + ":=" + sError.getMessage();
        toReturn.setMessage(message1 + message2);
        return toReturn;
    }

    /**
     * Implements NetworkHandler to handle registration requests from
     * SlaveServers.
     *
     */
    public class TPCRegistrationHandler implements NetworkHandler {

        public ThreadPool threadpool = null;

        public TPCRegistrationHandler() {
            // Call the other constructor
            this(1);
        }

        public TPCRegistrationHandler(int connections) {
            threadpool = new ThreadPool(connections);
        }

        @Override
        public void handle(Socket client) throws IOException {
            RegistrationHandler handler = new RegistrationHandler(client);
            try {
                threadpool.addToQueue(handler);
            } catch (InterruptedException e) {
                // Nothing can be done in this case
                throw new RuntimeException(e);
            }
        }

        public class RegistrationHandler implements Runnable {

            public Socket client = null;

            public RegistrationHandler(Socket client) {
                this.client = client;
            }

            @Override
            public void run() {
                try {
                    KVMessage regMsg = new KVMessage(client);
                    if("register".equals(regMsg.getMsgType())) {
                        SlaveInfo info = new SlaveInfo(regMsg.getMessage());
                        // Search through our list to ensure it isn't there.
                        for(int i = 0; i < keySpace.size(); i++) {
                            if(keySpace.get(i).getSlaveID() == info.getSlaveID()) {
                                keySpace.set(i, info);
                                new KVMessage("resp", "Success").sendMessage(client);
                                return;
                            }
                        }
                        // If not, add a new node into the space where it belongs
                        for(int i = 0; i < keySpace.size(); i++) {
                            if(isLessThanUnsigned(info.getSlaveID(), keySpace.get(i).getSlaveID())) {
                                keySpace.add(i, info);
                                return;
                            }
                        }
                        // If we didn't add it, add it to the end
                        keySpace.add(info);
                        new KVMessage("resp", "Success").sendMessage(client);
                    } else {
                        new KVMessage("resp", "Error invalid message to register server").sendMessage(client);
                    }
                } catch (KVException e) {
                    try {
                        e.getMsg().sendMessage(client);
                    } catch (KVException ignore) {
                        // This case can be ignored by spec.
                    }
                }
            }
        }
    }

    /**
     * Data structure to maintain information about SlaveServers
     *
     */
    public class SlaveInfo {
        // 64-bit globally unique ID of the SlaveServer
        public long slaveID = -1;
        // Name of the host this SlaveServer is running on
        public String hostName = null;
        // Port which SlaveServer is listening to
        public int port = -1;

        /**
         *
         * @param slaveInfo as "SlaveServerID@HostName:Port"
         * @throws KVException
         */
        public SlaveInfo(String slaveInfo) throws KVException {
            String[] idAndUrl = slaveInfo.split("@");
            try {
                this.slaveID = Long.parseLong(idAndUrl[0]);
                String[] hostPort = idAndUrl[1].split(":");
                this.hostName = hostPort[0];
                this.port = Integer.parseInt(hostPort[1]);
            } catch (Exception e) {
                throw new KVException(new KVMessage("resp", "Unknown Error: improperly formatted slaveInfo string"));
            }
        }

        public long getSlaveID() {
            return slaveID;
        }

        public Socket connectHost() throws KVException {
            Socket connection;
            try {
                connection = new Socket(hostName, port);
            } catch (UnknownHostException e) {
                throw new KVException(new KVMessage("resp", "Network Error: Could not connect"));
            } catch (IOException e) {
                throw new KVException(new KVMessage("resp", "Network Error: Could not create socket"));
            }
            try {
                connection.setSoTimeout(TIMEOUT_MILLISECONDS);
            } catch (SocketException e) {
                throw new KVException(new KVMessage("resp", "Unknown Error: SocketException creating timeout " + e.getLocalizedMessage()));
            }
            return connection;
        }

        public void closeHost(Socket sock) throws KVException {
            try {
                sock.close();
            } catch (IOException e) {
                throw new KVException(new KVMessage("resp", "Network Error: Could not close socket"));
            }
        }
    }

    /**
     * Stop the master server from listening for connections for registration.
     */
    public void stop() {
        regServer.stop();
        regServer.closeSocket();
    }
}
