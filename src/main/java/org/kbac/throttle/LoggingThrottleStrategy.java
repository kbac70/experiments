package org.kbac.throttle;

import org.springframework.util.Assert;

/**
 * Created by krzysztof on 21/12/2015.
 */
public abstract class LoggingThrottleStrategy implements ThrottleStrategy {

    @Override
    public final boolean handle(Bucket bucket) {
        Assert.notNull(bucket, "bucket must not be null");

        final long dropCount = bucket.addDrop();
        if (dropCount == bucket.getMaxDropCount()) {
            handleFullBucket(dropCount, bucket.getMaxDropCount());
        }

        return true;
    }

    protected abstract void handleFullBucket(long dropCount, long maxDropCount);
}
