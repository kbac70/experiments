package org.kbac.throttle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * Created by krzysztof on 21/12/2015.
 *
 * Thread safe leaky bucket implementation allowing to ensure only given number of drops can make their
 * way into the bucket before it gets periodically drained.
 */
public final class LeakyBucket {

    public static final long DEFAULT_DRAIN_INTERVAL_MILLIS = Meter.THROTTLE_INTERVAL_MILLIS_DEFAULT;

    public static final long NOTHING_DRAINED = -1;

    private static final Logger LOGGER = LoggerFactory.getLogger(LeakyBucket.class);

    private final String name;

    private final long maxDropCount;

    private final long drainIntervalMillis;

    private volatile long lastDrainedMillis;

    private volatile long dropCount;

    private volatile long leakedCount;


    public LeakyBucket(final String name, final long maxDropCount) {
        this(name, maxDropCount, DEFAULT_DRAIN_INTERVAL_MILLIS);
    }

    public LeakyBucket(final String name, final long maxDropCount, long drainIntervalMillis) {
        Assert.hasLength(name, "name must not be empty");
        Assert.isTrue(maxDropCount > 0, "maxDropCount must be greater than 0");
        Assert.isTrue(drainIntervalMillis > 0, "drainIntervalMillis must be greater than 0");

        this.name = name;
        this.maxDropCount = maxDropCount;
        this.drainIntervalMillis = drainIntervalMillis;

        drain();
    }

    /**
     * Invoke this method to add a drop to this bucket.
     * @return maxDropCount when bucket is full, number of drops within drain interval otherwise
     */
    public synchronized long addDrop() {
        final long currentDrops;
        if (!isFull()) {
            LOGGER.debug("added drop: {}", this);
            currentDrops = this.dropCount++;
        } else {
            this.leakedCount++;
            LOGGER.debug("already full: {}", this);
            currentDrops = maxDropCount;
        }
        return currentDrops;
    }

    /**
     * Invoke this method to request that all the drops are drained from this bucket.
     * @return NOTHING_DRAINED when drain invocation got executed within the drainIntervalMillis,
     * number of drained drops otherwise
     */
    public synchronized long drain() {
        final long currentTimeMillis = System.currentTimeMillis();
        final long drained;
        if (currentTimeMillis - lastDrainedMillis < drainIntervalMillis) {
            LOGGER.trace("not drained: {}", this);
            drained = NOTHING_DRAINED;
        } else {
            this.lastDrainedMillis = currentTimeMillis;
            LOGGER.debug("drained: {}", this);
            drained = this.dropCount;
            this.dropCount = 0;
            this.leakedCount = 0;
        }
        return drained;
    }

    public long getDrainIntervalMillis() {
        return this.drainIntervalMillis;
    }

    public long getMaxDropCount() {
        return this.maxDropCount;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name + "[" + this.dropCount + "|" + this.leakedCount + "|" + this.maxDropCount + "]@"
                + this.drainIntervalMillis + "ms";
    }

    protected long getDropCount() {
        return this.dropCount;
    }

    protected boolean isFull() {
        return this.dropCount == this.maxDropCount;
    }
}
