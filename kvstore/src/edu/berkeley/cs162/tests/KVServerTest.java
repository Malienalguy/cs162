package edu.berkeley.cs162.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.berkeley.cs162.KVCache;
import edu.berkeley.cs162.KVException;
import edu.berkeley.cs162.KVServer;
import edu.berkeley.cs162.KVStore;

/**
 * @author hkothari
 */
public final class KVServerTest {

    private static final String TEST_KEY = "testkey";
    private static final String TEST_VALUE = "testvalue";

    private KVCache cache;
    private KVStore store;
    private KVServer server;

    @Before
    public void setUp() {
        cache = new KVCache(1, 5);
        store = new KVStore();
        server = new KVServer(store, cache);
    }

    @After
    public void tearDown() {
        server = null;
        store = null;
        cache = null;
    }

    @Test
    public void testPut() throws KVException {
        // For sanity this should not throw an error
        server.put(TEST_KEY, TEST_VALUE);
        // The value should be in the store but not in the cache.
        assertEquals(null, cache.get(TEST_KEY));
        assertEquals(TEST_VALUE, store.get(TEST_KEY));
    }

    @Test
    public void testGet() throws KVException {
        // We should not have an errors thrown here
        server.put(TEST_KEY, TEST_VALUE);
        String result = server.get(TEST_KEY);
        assertEquals(TEST_VALUE, result);
        // The value should now be in the cache.
        assertEquals(TEST_VALUE, cache.get(TEST_KEY));
    }

    @Test
    public void testDel() throws KVException {
        server.put(TEST_KEY, TEST_VALUE);
        server.del(TEST_KEY);

        assertEquals(null, cache.get(TEST_KEY));
        try {
            store.get(TEST_VALUE);
            fail("test key was still present in store after delete");
        } catch(KVException e) {
            // Fall through, this means the key wasn't in the store
        }

        try {
            server.get(TEST_KEY);
            fail("Should not be able to get value for deleted key.");
        } catch(KVException e) {
            // All good, expected exception throw
        }
    }

    @Test
    public void testCacheDelete() throws KVException {
        // Put
        server.put(TEST_KEY, TEST_VALUE);
        // Get - ensure it returns the value we put
        server.get(TEST_KEY);
        // Del - ensure it's removed from cache and store
        server.del(TEST_KEY);
        assertEquals(null, cache.get(TEST_KEY));
        // Get - ensure we get an exception meaning the value was removed from cache and server
        try {
            server.get(TEST_KEY);
            fail("Should not be able to get value for deleted key.");
        } catch(KVException e) {
            // All good, expected exception throw
        }
    }

    @Test
    public void testCachePut() throws KVException {
        // Put a value in the server
        server.put(TEST_KEY, TEST_VALUE);
        assertEquals(null, cache.get(TEST_KEY));
        // Get it
        assertEquals(TEST_VALUE, server.get(TEST_KEY));
        // Ensure the cache now has the other value
        assertEquals(TEST_VALUE, cache.get(TEST_KEY));
    }

    @Test
    public void testHasKey() throws KVException {
        server.put(TEST_KEY, TEST_VALUE);
        assertTrue(server.hasKey(TEST_KEY));
        try {
            server.hasKey("notrealkey");
            fail("hasKey on fake key does not throw exception.");
        } catch (KVException e) {
            // Fall through
        }
    }
}
