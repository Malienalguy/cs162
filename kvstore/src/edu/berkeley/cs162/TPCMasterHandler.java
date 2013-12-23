/**
 * Handle TPC connections over a socket interface
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
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Implements NetworkHandler to handle 2PC operation requests from the Master/
 * Coordinator Server
 *
 */
public class TPCMasterHandler implements NetworkHandler {

    public KVServer kvServer = null;
    public ThreadPool threadpool = null;
    public TPCLog tpcLog = null;
    public long slaveID = -1;

    // Used to handle the "ignoreNext" message
    public boolean ignoreNext = false;

    // Stored phase-1 request message from TPCMaster
    public KVMessage originalMessage = null;

    // Whether we sent back an abort decision in phase 1. Used and checked by
    // autograder. Is not used for any other logic.
    public boolean aborted = false;

    public TPCMasterHandler(KVServer keyserver) {
        this(keyserver, 1);
    }

    public TPCMasterHandler(KVServer keyserver, long slaveID) {
        this.kvServer = keyserver;
        this.slaveID = slaveID;
        threadpool = new ThreadPool(1);
    }

    public TPCMasterHandler(KVServer kvServer, long slaveID, int connections) {
        this.kvServer = kvServer;
        this.slaveID = slaveID;
        threadpool = new ThreadPool(connections);
    }

    /**
     * Set TPCLog after it has been rebuilt.
     *
     * @param tpcLog
     */
    public void setTPCLog(TPCLog tpcLog) {
        this.tpcLog = tpcLog;
    }

    /**
     * Registers the slave server with the master.
     *
     * @param masterHostName
     * @param server
     *            SocketServer used by this slave server (contains the hostName
     *            and a random port)
     * @throws UnknownHostException
     * @throws IOException
     * @throws KVException
     */
    public void registerWithMaster(String masterHostName, SocketServer server)
            throws UnknownHostException, IOException, KVException {
        AutoGrader.agRegistrationStarted(slaveID);

        Socket master = new Socket(masterHostName, 9090);
        KVMessage regMessage = new KVMessage("register", slaveID + "@"
                + server.getHostname() + ":" + server.getPort());
        regMessage.sendMessage(master);

        // Receive master response. Response should always be success.
        new KVMessage(master);

        master.close();
        AutoGrader.agRegistrationFinished(slaveID);
    }

    @Override
    public void handle(Socket client) throws IOException {
        AutoGrader.agReceivedTPCRequest(slaveID);
        Runnable r = new MasterHandler(kvServer, client);
        try {
            threadpool.addToQueue(r);
        } catch (InterruptedException e) {
            return; // ignore this error
        }
        AutoGrader.agFinishedTPCRequest(slaveID);
    }

    public class MasterHandler implements Runnable {

        public KVServer keyserver = null;
        public Socket client = null;

        public void closeConn() {
            try {
                client.close();
            } catch (IOException e) {
            }
        }

        public MasterHandler(KVServer keyserver, Socket client) {
            this.keyserver = keyserver;
            this.client = client;
        }

        public void abort(String opId, String reason) {
            KVMessage toAbort;
            try {
                toAbort = new KVMessage("abort");
            } catch (KVException e) {
                // This won't happen.
                throw new RuntimeException(e);
            }
            toAbort.setMessage(reason);
            toAbort.setTpcOpId(opId);
            try {
                toAbort.sendMessage(client);
                if (tpcLog != null) {
                    tpcLog.appendAndFlush(toAbort);
                }
            } catch (KVException e) {
                // If this happens we try to send an error message:
                try {
                    e.getMsg().sendMessage(client);
                } catch (KVException e1) {
                    // We can't handle this case..
                    throw new RuntimeException(e1);
                }
            }
        }

        public void respond(String msg, String opID, String key, String val) {
            KVMessage response;
            try {
                response = new KVMessage(msg);
            } catch (KVException e) {
                // Won't happen
                throw new RuntimeException(e);
            }

            response.setTpcOpId(opID);

            if (key != null && val != null) {
                // Used in the handleGet() method only
                response.setKey(key);
                response.setValue(val);
            }

            try {
                response.sendMessage(client);
                if (tpcLog != null) {
                    tpcLog.appendAndFlush(response);
                }
            } catch (KVException e) {
                try {
                    e.getMsg().sendMessage(client);
                } catch (KVException e1) {
                    // We can just fail this case:
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void run() {
            KVMessage msg;
            try {
                msg = new KVMessage(client);
            } catch (KVException e) {
                // We can't do anything without the message
                throw new RuntimeException(e);
            }

            String key = msg.getKey();
            String msgType = msg.getMsgType();

            if ((msgType.equals("delreq") || msgType.equals("putreq")) && ignoreNext) {
                ignoreNext = false;
                // Do nothing in the case of ignore
                try {
                    new KVMessage("resp", "IgnoreNext Error: SlaveServer SlaveServerID has ignored this 2PC request during the first phase").sendMessage(client);
                } catch (KVException e) {
                    try {
                        e.getMsg().sendMessage(client);
                    } catch (KVException e1) {
                        throw new RuntimeException(e1);
                    }
                }
            } else if (msgType.equals("getreq")) {
                handleGet(msg, key);
            } else if (msgType.equals("putreq")) {
                handlePut(msg, key);
            } else if (msgType.equals("delreq")) {
                handleDel(msg, key);
            } else if (msgType.equals("ignoreNext")) {
                // Set ignoreNext to true.
                ignoreNext = true;
                try {
                    // Send back an acknowledgment
                    new KVMessage("resp", "Success").sendMessage(client);
                } catch (KVException e) {
                    // We can't recover from this case.
                    throw new RuntimeException(e);
                }
            } else if (msgType.equals("commit") || msgType.equals("abort")) {

                if (ignoreNext) {
                    ignoreNext = false;
                    return;
                }

                // Check in TPCLog for the case when SlaveServer is restarted
                if (tpcLog.hasInterruptedTpcOperation()) {
                    originalMessage = tpcLog.getInterruptedTpcOperation();
                }
                // originalMessage is either the passed in argument or
                // tpcLog.getInterruptedTpcOperation
                handleMasterResponse(msg, originalMessage, aborted);

                originalMessage = null;
                aborted = false;
            } else {
                try {
                    new KVMessage("resp", "Error: message type unknown").sendMessage(client);
                } catch (KVException e) {
                    // We can fall through in this case.
                }
            }
            // Finally, close the connection
            closeConn();
        }

        /* Handle a get request from the master */
        public void handleGet(KVMessage msg, String key) {
            AutoGrader.agGetStarted(slaveID);

            try {
                String val = keyserver.get(key);
                String opID = msg.getTpcOpId();
                respond("resp", opID, key, val);
            } catch (KVException e) {
                abort(msg.getTpcOpId(), e.getMsg().getMessage());
            }

            AutoGrader.agGetFinished(slaveID);
        }

        /* Handle a phase-1 2PC put request from the master */
        public void handlePut(KVMessage msg, String key) {
            AutoGrader.agTPCPutStarted(slaveID, msg, key);
            // Store for use in the second phase
            originalMessage = new KVMessage(msg);
            aborted = false;

            tpcLog.appendAndFlush(msg);
            respond("ready", msg.getTpcOpId(), null, null);

            AutoGrader.agTPCPutFinished(slaveID, msg, key);
        }

        /* Handle a phase-1 2PC del request from the master */
        public void handleDel(KVMessage msg, String key) {
            AutoGrader.agTPCDelStarted(slaveID, msg, key);
            // Store for use in the second phase
            originalMessage = new KVMessage(msg);

            try {
                    kvServer.hasKey(key); // Can't delete it if it doesn't exist
                    aborted = false;
                    tpcLog.appendAndFlush(msg);
                    respond("ready", msg.getTpcOpId(), null, null);
            } catch (KVException e) {
                aborted = true;
                abort(msg.getTpcOpId(), e.getMsg().getMessage());
            }

            AutoGrader.agTPCDelFinished(slaveID, msg, key);
        }

        /**
         * Second phase of 2PC
         *
         * @param masterResp
         *            Global decision taken by the master
         * @param origMsg
         *            Message from the actual client (received via the
         *            coordinator/master)
         * @param origAborted
         *            Did this slave server abort it in the first phase
         */
        public void handleMasterResponse(KVMessage masterResp,
                KVMessage origMsg, boolean origAborted) {
            AutoGrader.agSecondPhaseStarted(slaveID, origMsg, origAborted);

            if(!origAborted) {
                if(masterResp.getMsgType().equals("abort")) {
                    // TODO: do nothing?
                } else if (masterResp.getMsgType().equals("commit")) {
                    if (origMsg.getMsgType().equals("delreq")) {
                        try {
                            kvServer.del(origMsg.getKey());
                        } catch (KVException e) {
                            // This cannot happen with TPC
                            throw new RuntimeException(e);
                        }
                    } else if (origMsg.getMsgType().equals("putreq")) {
                        try {
                            kvServer.put(origMsg.getKey(), origMsg.getValue());
                        } catch (KVException e) {
                            // This cannot happen with TPC
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            // Send our response
            try {
                KVMessage ack = new KVMessage("ack");
                ack.setTpcOpId(origMsg.getTpcOpId());
                ack.sendMessage(client);
                if (tpcLog != null)
                    tpcLog.appendAndFlush(masterResp);
            } catch (KVException e) {
                try {
                    e.getMsg().sendMessage(client);
                } catch (KVException e1) {
                    throw new RuntimeException(e1);
                }
            }

            AutoGrader.agSecondPhaseFinished(slaveID, origMsg, origAborted);
        }
    }
}
