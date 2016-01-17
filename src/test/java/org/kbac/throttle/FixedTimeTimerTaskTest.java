package org.kbac.throttle;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class FixedTimeTimerTaskTest {

    FixedTimeTimerTask task;

    ConcurrentMap<String, LeakyBucketStub> buckets;

    private static final class LeakyBucketStub extends LeakyBucket {

        private static final long MAX_DROPS = 100;

        final long lastUsedMillis;

        boolean drained;

        public LeakyBucketStub(String name, long lastUsedMillis) {
            super(name, MAX_DROPS);
            this.lastUsedMillis = lastUsedMillis;
            this.drained = false;
        }

        @Override
        public synchronized long drain() {
            this.drained = true;
            return 0;
        }

        @Override
        public long getLastUsedMillis() {
            return this.lastUsedMillis;
        }

        public boolean isDrained() {
            return drained;
        }
    }


    enum Buckets {
          TO_BE_SKIPPED
        , TO_BE_REMOVED
        , TO_BE_DRAINED
    }


    @Before
    public void setUp() throws Exception {
        this.buckets = new ConcurrentHashMap<>();

        final long currentMillis = System.currentTimeMillis() - 1;
        this.buckets.put(Buckets.TO_BE_SKIPPED.name()
                , new LeakyBucketStub(Buckets.TO_BE_SKIPPED.name()
                , currentMillis
        ));
        this.buckets.put(Buckets.TO_BE_DRAINED.name()
                , new LeakyBucketStub(Buckets.TO_BE_DRAINED.name()
                , currentMillis - LeakyBucket.DEFAULT_DRAIN_INTERVAL_MILLIS_DEFAULT
        ));
        this.buckets.put(Buckets.TO_BE_REMOVED.name()
                , new LeakyBucketStub(Buckets.TO_BE_REMOVED.name()
                , currentMillis - FixedTimeTimerTask.IDLE_BUCKET_REMOVE_MILLIS_DEFAULT
        ));

        this.task = new FixedTimeTimerTask(buckets);
    }

    @Test
    public void removesUnusedBuckets() throws Exception {
        assertEquals("invalid test setup", 3, this.buckets.size());
        assertTrue("missing bucket: " + Buckets.TO_BE_REMOVED.name()
                , this.buckets.containsKey(Buckets.TO_BE_REMOVED.name()));

        task.run();

        assertEquals("invalid size of buckets", 2, buckets.size());
        assertFalse("bucket should be removed: " + Buckets.TO_BE_REMOVED.name()
                , this.buckets.containsKey(Buckets.TO_BE_REMOVED.name()));
    }

    @Test
    public void drainsBuckets() {
        assertEquals("invalid test setup", 3, this.buckets.size());
        assertFalse("invalid initial drain status for bucket: " + Buckets.TO_BE_DRAINED.name()
                , this.buckets.get(Buckets.TO_BE_DRAINED.name()).isDrained());

        task.run();

        assertFalse("buckets should not be empty", buckets.isEmpty());
        for (Map.Entry<String, ? extends LeakyBucket> entry : buckets.entrySet()) {
            assertTrue("bucket should be drained:  " + entry.getKey()
                    , this.buckets.get(entry.getKey()).isDrained());
        }
    }
}