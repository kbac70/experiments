package org.kbac.throttle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class BucketUtils {

    public static final String BUCKET_NAME = "TEST-BUCKET";

    public static final int MAX_DROP_COUNT = 1000;

    public static void fillBucket(final LeakyBucket bucket) {
        assertEquals("expected empty bucket", 0, bucket.getDropCount());
        for (int i = 0; i < MAX_DROP_COUNT; i++) {
            assertTrue("drops should accumulate until bucket full #" + i, bucket.addDrop() <= MAX_DROP_COUNT);
        }

        assertTrue("bucket should be full", bucket.isFull());
        assertEquals("invalid drop count for a full bucket", MAX_DROP_COUNT, bucket.getDropCount());
    }

    public static void fillBucketAndWait(final LeakyBucket bucket) throws InterruptedException {
        fillBucket(bucket);

        Thread.sleep(bucket.getDrainIntervalMillis());
    }


    private BucketUtils() {
        //no c-tor
    }
}
