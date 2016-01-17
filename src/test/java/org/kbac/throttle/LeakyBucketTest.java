package org.kbac.throttle;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.kbac.throttle.BucketUtils.*;

/**
 * Created by krzysztof on 16/01/2016.
 */
public class LeakyBucketTest {

    LeakyBucket bucket;

    @Before
    public void setUp() throws Exception {
        bucket = new LeakyBucket(BUCKET_NAME, MAX_DROP_COUNT);
    }

    @Test
    public void addsDrop() throws Exception {
        assertEquals("expected empty bucket", 0, bucket.getDropCount());
        bucket.addDrop();
        assertEquals("invalid number of drops", 1, bucket.getDropCount());
    }

    @Test
    public void addsDropLeaks() throws Exception {
        fillBucket(this.bucket);

        assertTrue("bucket should leak when full", bucket.addDrop() == MAX_DROP_COUNT );
    }

    @Test
    public void drainsOk() throws Exception {
        final int DROP_COUNT = 10;

        for (int i = 0; i < DROP_COUNT; i++) {
            bucket.addDrop();
        }

        Thread.sleep(bucket.getDrainIntervalMillis());

        assertEquals("invalid number of drops drained from the bucket", DROP_COUNT, bucket.drain());
        assertTrue("drained bucket should be empty", bucket.getDropCount() == 0);
    }

    @Test
    public void nextDrainRequestWithinDrainIntervalRejected() throws InterruptedException {
        fillBucketAndWait(this.bucket);

        assertEquals("invalid number of drops", MAX_DROP_COUNT, bucket.drain());
        assertTrue("next drain request within drain interval should be rejected", bucket.drain() == LeakyBucket.NOTHING_DRAINED);
    }

    @Test
    public void defaultDrainIntervalMillisValid() throws Exception {
        assertEquals(LeakyBucket.DEFAULT_DRAIN_INTERVAL_MILLIS, bucket.getDrainIntervalMillis());
    }

    @Test
    public void maxDropCountValid() throws Exception {
        assertEquals("invalid maxDropCount", MAX_DROP_COUNT, bucket.getMaxDropCount());
    }

    @Test
    public void nameValid() throws Exception {
        assertSame("invalid bucket name", BUCKET_NAME, bucket.getName());
    }

    @Test
    public void toStringNevetEmpty() throws Exception {
        assertTrue("toString should never be empty", bucket.toString().length() > 0);
    }
}