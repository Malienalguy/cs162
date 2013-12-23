package edu.berkeley.cs162.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;

import org.junit.Before;
import org.junit.Test;

import edu.berkeley.cs162.KVCache;

/**
 * @author hkothari
 *
 */
public final class KVCacheTest {

    private KVCache cache;

    @Before
    public void setUp() {
        cache = new KVCache(1, 2);
    }

    @Test
    public void testPutGet() {
        cache.put("testKey", "testValue");
        assertEquals("testValue", cache.get("testKey"));
    }

    @Test
    public void testDel() {
        cache.put("testKey", "testValue");
        cache.del("testKey");
        assertEquals(null, cache.get("testKey"));
    }

    @Test
    public void testOverwrite() {
        cache.put("testKey", "testValue");
        cache.put("testKey", "newValue");
        assertEquals("newValue", cache.get("testKey"));
    }

    @Test
    public void testReplacement() {
        // Since we have a 1x2 cache the third element should push
        // one of them out.

        cache.put("1", "one");
        cache.put("2", "two");
        cache.put("3", "three");
        // The oldest value should get pushed out in this case.
        assertEquals(null, cache.get("1"));

        // We put "2" back in so now 3 should get pushed out.
        cache.put("2", "two");
        cache.put("4", "four");
        assertEquals(null, cache.get("3"));

        // 2 should no longer be referenced after the second chance though now so it should
        // be on the chopping block.
        cache.put("5", "five");
        assertEquals(null, cache.get("2"));

        // Finally 4 should be on the chopping block, but we will get it, so now 5 should be
        // taken out.
        cache.get("4");
        cache.put("6", "six");
        assertEquals(null, cache.get("5"));
    }

    @Test
    public void toXML() {
        cache.put("1", "one");
        String xml = cache.toXML();
        assertThat(xml, containsString("<Set Id=\"0\">"));
        assertThat(xml, containsString("<CacheEntry isReferenced=\"false\" isValid=\"true\">"));
        assertThat(xml, containsString("<CacheEntry isReferenced=\"false\" isValid=\"false\">"));
        cache.get("1");
        xml = cache.toXML();
        assertThat(xml, containsString("<CacheEntry isReferenced=\"true\" isValid=\"true\">"));
    }
}
