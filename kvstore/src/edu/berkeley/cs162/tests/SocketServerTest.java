package edu.berkeley.cs162.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.berkeley.cs162.NetworkHandler;
import edu.berkeley.cs162.SocketServer;

/**
 * @author hkothari
 *
 */
public final class SocketServerTest {

    private static final String TEST_MESSAGE = "test";
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8123;
    private static final long STOP_DELAY_MS = 2000;

    private ServerThread st;
    private TestHandler handler;

    @Before
    public void setUp() {
        // Spawn our listener server
        st = new ServerThread();
        handler = new TestHandler();
        st = new ServerThread();
        st.server.addHandler(handler);
        st.start();
    }

    @After
    public void tearDown() {
        if(st != null) {
            st.server.stop();
            st.server.closeSocket();
        }
        st = null;
        handler = null;
    }

    @Test
    public void testListening() throws IOException {
        // Check to make sure it's listening
        Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
        socket.getOutputStream().write(TEST_MESSAGE.getBytes());
        // All goes well if this completes, if the server isn't listening we'll get an exception.
        socket.close();
    }

    @Test
    public void testStop() throws InterruptedException {
        // Wait for the server to run, then stop the server and wait for a few seconds to ensure that it stops.
        Thread.sleep(STOP_DELAY_MS);
        st.server.stop();
        Thread.sleep(STOP_DELAY_MS);

        // Write to the server, since it's still listening it should go through but the handler won't handle it.
        try {
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            socket.getOutputStream().write(TEST_MESSAGE.getBytes("UTF-8"));
            socket.close();
        } catch (IOException e) {
            fail("Socket failed to write to stopped but not closed server" + e);
        }
        assertEquals(0, handler.getRecieved());

        // Close the connection now, ensure that connections fail
        st.server.closeSocket();
        try {
            Socket s = new Socket(SERVER_HOST, SERVER_PORT);
            s.getOutputStream().write(TEST_MESSAGE.getBytes("UTF-8"));
            fail("Socket to closed server failed to throw exception.");
        } catch(IOException e) {
            // Fall through, all is well
        }
    }

    @Test
    public void testAcceptsMultipleConnections() throws InterruptedException {
        // Start a bunch of listeners
        List<RequestThread> requests = new ArrayList<RequestThread>();
        for(int i = 0; i < 10; i++) {
            RequestThread r = new RequestThread();
            r.start();
            requests.add(r);
        }
        // Wait for them to finish
        for(RequestThread r : requests) {
            r.join();
        }
        // Check the handler to ensure that all is well
        assertEquals(10, handler.getRecieved());
    }

    private class ServerThread extends Thread {
        public final SocketServer server = new SocketServer(SERVER_HOST, SERVER_PORT);

        @Override
        public void run() {
            try {
                server.connect();
                server.run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class RequestThread extends Thread {

        @Override
        public void run() {
            try {
                Socket s = new Socket(SERVER_HOST, SERVER_PORT);
                OutputStream out = s.getOutputStream();
                out.write(TEST_MESSAGE.getBytes("UTF-8"));
                s.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    private class TestHandler implements NetworkHandler {

        private final AtomicInteger recieved = new AtomicInteger();

        @Override
        public synchronized void handle(Socket client) throws IOException {
            InputStream stream = client.getInputStream();
            byte[] bytes = new byte[100];
            stream.read(bytes);
            // We trim because the socket/streams add some extra whitespace. harmless in this case
            String contents = new String(bytes, "UTF-8").trim();
            if(TEST_MESSAGE.equals(contents)) {
                recieved.incrementAndGet();
            }
        }

        public synchronized int getRecieved() {
            return recieved.get();
        }
    }
}
