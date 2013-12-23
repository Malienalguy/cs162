/**
 *
 */
package edu.berkeley.cs162.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;

import static org.junit.matchers.JUnitMatchers.containsString;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.berkeley.cs162.KVException;
import edu.berkeley.cs162.KVMessage;
import edu.berkeley.cs162.KVServer;
import edu.berkeley.cs162.TPCLog;
import edu.berkeley.cs162.TPCMasterHandler;
import edu.berkeley.cs162.TPCMasterHandler.MasterHandler;

/**
 * @author hkothari
 *
 */
public final class TPCHandlerLogTest {

    private static final String TEST_KEY1 = "testkey1";
    private static final String TEST_KEY2 = "testkey2";
    private static final String TEST_VALUE1 = "testvalue1";
    private static final String TEST_VALUE2 = "testvalue2";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public KVServer server = null;
    public KVServer newServer = null;
    public TPCMasterHandler handler = null;
    public TPCLog log = null;

    @Before
    public void setUp() throws IOException {
        server = new KVServer(5, 5);
        newServer = new KVServer(5, 5);
        handler = new TPCMasterHandler(server);
        log = new TPCLog(tempFolder.newFile().getAbsolutePath(), newServer);
        handler.setTPCLog(log);
    }

    @Test
    public void testSimpleHandleRebuild() throws KVException, IOException {
        KVMessage putReq = new KVMessage("putreq");
        putReq.setKey(TEST_KEY1);
        putReq.setValue(TEST_VALUE1);
        putReq.setTpcOpId("1");
        String putResult = sendMessageToHandler(putReq, handler);
        assertThat(putResult, containsString("ready"));

        // Ensure that it didn't get put in the store yet
        try {
            server.hasKey(TEST_KEY1);
            fail("Value was stored before commit message");
        } catch (KVException e) {
            // Expected
        }

        KVMessage commit = new KVMessage("commit");
        commit.setTpcOpId("1");
        String commitResult = sendMessageToHandler(commit, handler);
        assertThat(commitResult, containsString("ack"));

        // Ensure that it's in the server now.
        assertEquals(TEST_VALUE1, server.get(TEST_KEY1));

        // Add a second value to our server
        KVMessage put2 = new KVMessage("putreq");
        put2.setKey(TEST_KEY2);
        put2.setValue(TEST_VALUE2);
        put2.setTpcOpId("2");
        String put2Result = sendMessageToHandler(put2, handler);
        assertThat(put2Result, containsString("ready"));

        // Rebuild our server into newServer
        log.rebuildKeyServer();

        // Ensure that our new server only has key 1
        assertEquals(TEST_VALUE1, newServer.get(TEST_KEY1));
        try {
            newServer.hasKey(TEST_KEY2);
            fail("newServer has second test key and it shouldn't");
        } catch (KVException e) {
            // Expected
        }

        // and ensure that it has an interrupted operation of
        assertEquals("ready", log.getInterruptedTpcOperation().getMsgType());
    }


    /**
     * Sends the message to the handler and returns the result from the socket.
     */
    private String sendMessageToHandler(KVMessage msg, TPCMasterHandler handler) throws KVException, IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(msg.toXML().getBytes());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Socket mockedSocket = mock(Socket.class);
        when(mockedSocket.getInputStream()).thenReturn(is);
        when(mockedSocket.getOutputStream()).thenReturn(os);
        TPCMasterHandler.MasterHandler h = handler.new MasterHandler(server, mockedSocket);
        h.run();
        return os.toString();
    }

    @After
    public void tearDown() {
        log = null;
        handler = null;
        server = null;

    }

}
