package edu.berkeley.cs162.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author hkothari
 *
 */
@RunWith(Suite.class)
@SuiteClasses({
    EndToEndTests.class,
    KVCacheTest.class,
    KVMessageTest.class,
    KVServerTest.class,
    KVStoreTest.class,
    SocketServerTest.class,
    ThreadPoolTest.class,
    TPCHandlerLogTest.class
})
public final class AllTests {

}
