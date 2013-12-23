package edu.berkeley.cs162.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.berkeley.cs162.KVException;
import edu.berkeley.cs162.KVStore;

/**
 * @author malienalguy
 * @author hkothari
 */

public final class KVStoreTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private KVStore store;

    @Before
    public void setUp() {
        store = new KVStore();
    }

    @Test
    public void testDumpRestoreEquality() throws KVException, IOException {
        File dumpFile = tempFolder.newFile("dumpFile.xml");

        store.put("Maliena", "20");
        // Just make sure that put/get work, for sanity
        assertEquals("20", store.get("Maliena"));
        store.put("Hamel", "19");
        store.put("Bryan", "24");
        store.put("EunSeon", "20");

        store.dumpToFile(dumpFile.getAbsolutePath());

        store.del("Bryan");
        store.del("Hamel");

        store.put("Lisa", "50");
        store.put("Maliena", "21");

        store.restoreFromFile(dumpFile.getAbsolutePath());

        // Added fields should be removed
        try {
            store.get("Lisa");
            fail("Failed to reset store on restore. New fields still present.");
        } catch (KVException e) {
            // Fall through, we expect an exception
        }

        // Changed fields should be the old value
        assertEquals("20", store.get("Maliena"));

        // Removed fields should be restored
        assertEquals("19", store.get("Hamel"));

    }
}
