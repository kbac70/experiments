package org.kbac.throttle;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.kbac.throttle.BucketUtils.BUCKET_NAME;
import static org.kbac.throttle.BucketUtils.MAX_DROP_COUNT;
import static org.kbac.throttle.BucketUtils.fillBucket;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class LoggingThrottleStrategyTest {

    ThrottleStrategy strategy;
    
    LeakyBucket bucket;

    @Before
    public void setUp() throws Exception {
        strategy = new LoggingThrottleStrategy();
        bucket = new LeakyBucket(BUCKET_NAME, MAX_DROP_COUNT);
    }

    @Test
    public void dripIntoEmpty() throws Exception {
        final int DROP_COUNT = 300;
        for (int i = 0; i < DROP_COUNT; i++) {
            assertFalse("drop should fall into the bucket", strategy.dripAndCheckIfLeaked(bucket));
        }
    }

    @Test
    public void dripIntoFull() throws Exception {
        fillBucket(bucket);

        assertTrue("full bucket should leak drops", strategy.dripAndCheckIfLeaked(bucket));
    }

}