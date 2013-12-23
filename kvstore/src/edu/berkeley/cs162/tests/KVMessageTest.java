package edu.berkeley.cs162.tests;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;

import org.junit.Test;

import edu.berkeley.cs162.KVException;
import edu.berkeley.cs162.KVMessage;

/**
 * @author hkothari
 */
public final class KVMessageTest {

    @Test
    public void testKeyValue() throws KVException {
        KVMessage msg = new KVMessage("putreq");
        msg.setKey("testkey");
        msg.setValue("testvalue");
        String xml = msg.toXML();
        assertThat(xml, containsString("<Key>testkey</Key>"));
        assertThat(xml, containsString("<Value>testvalue</Value>"));
    }

    @Test
    public void testKey() throws KVException {
        KVMessage msg = new KVMessage("getreq");
        msg.setKey("testkey");
        String xml = msg.toXML();
        assertThat(xml, containsString("<Key>testkey</Key>"));
        assertThat(xml, not(containsString("<Value>")));
    }

    @Test
    public void testMessage() throws KVException {
        KVMessage msg = new KVMessage("resp", "testmessage");
        String xml = msg.toXML();
        assertThat(xml, containsString("<Message>testmessage</Message>"));
        assertThat(xml, not(containsString("<Key>")));
        assertThat(xml, not(containsString("<Value>")));
    }

    @Test
    public void testReadKeyValue() throws IOException, KVException {
        String testReadXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        		"<KVMessage type=\"putreq\">\n" +
        		"<Key>key</Key>\n" +
        		"<Value>value</Value>\n" +
        		"</KVMessage>";
        ByteArrayInputStream input = new ByteArrayInputStream(testReadXml.getBytes());
        Socket client = mock(Socket.class);
        when(client.getInputStream()).thenReturn(input);

        KVMessage msg = new KVMessage(client);
        assertEquals("key", msg.getKey());
        assertEquals("value", msg.getValue());
        assertEquals("putreq", msg.getMsgType());
    }

    @Test
    public void testReadKey() throws IOException, KVException {
        String testReadXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        		"<KVMessage type=\"getreq\">\n" +
        		"<Key>testkey</Key>\n" +
        		"</KVMessage>";
        ByteArrayInputStream input = new ByteArrayInputStream(testReadXml.getBytes());
        Socket client = mock(Socket.class);
        when(client.getInputStream()).thenReturn(input);

        KVMessage msg = new KVMessage(client);
        assertEquals("testkey", msg.getKey());
        assertEquals(null, msg.getValue());
        assertEquals("getreq", msg.getMsgType());
    }

    @Test
    public void testReadMessage() throws IOException, KVException {
        String testRespXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        		"<KVMessage type=\"resp\">\n" +
        		"<Message>Success</Message>\n" +
        		"</KVMessage>";
        ByteArrayInputStream input = new ByteArrayInputStream(testRespXml.getBytes());
        Socket client = mock(Socket.class);
        when(client.getInputStream()).thenReturn(input);

        KVMessage msg = new KVMessage(client);
        assertEquals(null, msg.getKey());
        assertEquals(null, msg.getValue());
        assertEquals("resp", msg.getMsgType());
        assertEquals("Success", msg.getMessage());
    }

    @Test
    public void testReadIllegalType() throws IOException {
        String testRespXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        		"<KVMessage type=\"notatype\">\n" +
        		"<Message>Success</Message>\n" +
        		"</KVMessage>";
        ByteArrayInputStream input = new ByteArrayInputStream(testRespXml.getBytes());
        Socket client = mock(Socket.class);
        when(client.getInputStream()).thenReturn(input);

        try {
            new KVMessage(client);
            fail("Failed to throw exception in invalid message type for KVMessage(socket)");
        } catch(KVException e) {
            // Fall through, all is well
        }
    }

    @Test
    public void testIllegalFormat() throws IOException {
        String testRespXml = "This is clearly invalid.";
        ByteArrayInputStream input = new ByteArrayInputStream(testRespXml.getBytes());
        Socket client = mock(Socket.class);
        when(client.getInputStream()).thenReturn(input);

        try {
            new KVMessage(client);
            fail("Failed to throw exception in invalid message format for KVMessage(socket)");
        } catch(KVException e) {
            // Fall through, all is well
        }
    }
}
