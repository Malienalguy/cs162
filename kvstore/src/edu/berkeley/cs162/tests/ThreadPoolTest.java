package edu.berkeley.cs162.tests;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import edu.berkeley.cs162.ThreadPool;

/**
 * @author hkothari
 */
public final class ThreadPoolTest {

    private final AtomicInteger count = new AtomicInteger(0);

    @Before
    public void setUp() {
        count.set(0);
    }

    @Test
    public void testIndividualPool() throws InterruptedException {
        ThreadPool pool = new ThreadPool(1);
        for(int i = 0; i < 100; i++) {
            pool.addToQueue(new TestJob());
        }
        // This should not take more than 3 seconds
        Thread.sleep(3000);
        assertEquals(100, count.get());
    }

    @Test
    public void testBigPool() throws InterruptedException {
        ThreadPool pool = new ThreadPool(10);
        for(int i = 0; i < 100; i++) {
            pool.addToQueue(new TestJob());
        }
        // This should not take more than 3 seconds
        Thread.sleep(3000);
        assertEquals(100, count.get());
    }

    private class TestJob implements Runnable {
        @Override
        public void run() {
            try {
                // We sleep just to take some time to ensure the
                // jobs get distributed
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // If this fails it's not a big deal
            }
            count.incrementAndGet();
        }
    }
}
