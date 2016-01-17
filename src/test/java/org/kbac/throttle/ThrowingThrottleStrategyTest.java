package org.kbac.throttle;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;
import static org.kbac.throttle.BucketUtils.BUCKET_NAME;
import static org.kbac.throttle.BucketUtils.MAX_DROP_COUNT;
import static org.kbac.throttle.BucketUtils.fillBucket;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class ThrowingThrottleStrategyTest {

    ThrottleStrategy strategy;

    LeakyBucket bucket;

    @Before
    public void setUp() throws Exception {
        strategy = new ThrowingThrottleStrategy();
        bucket = new LeakyBucket(BUCKET_NAME, MAX_DROP_COUNT);
    }

    @Test(expected = TooManyRequestsException.class)
    public void throwOnFullBucket() throws Exception {
        fillBucket(bucket);

        strategy.dripAndCheckIfLeaked(bucket);

        fail("expected to throw exception on full bucket");
    }
}