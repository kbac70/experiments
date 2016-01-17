/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Krzysztof Bacalski
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.kbac.throttle;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by krzysztof on 21/12/2015.
 * <p/>
 * Thread safe leaky bucket implementation allowing to ensure only given number of drops can make their
 * way into the bucket before it gets periodically drained. It leaks the drops when full.
 *
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class LeakyBucket {

    public static final long DEFAULT_DRAIN_INTERVAL_MILLIS_DEFAULT = Meter.THROTTLE_INTERVAL_MILLIS_DEFAULT;

    public static final long NOTHING_DRAINED = -1;

    private static final Logger LOGGER = LoggerFactory.getLogger(LeakyBucket.class);

    private final String name;

    private final long maxDropCount;

    private final long overflow;

    private final long drainIntervalMillis;

    private long lastUsedMillis;

    private long lastDrainedMillis;

    private long dropCount;

    private long leakedCount;


    public LeakyBucket(final String name, final long maxDropCount) {
        this(name, maxDropCount, DEFAULT_DRAIN_INTERVAL_MILLIS_DEFAULT);
    }

    public LeakyBucket(final String name, final long maxDropCount, long drainIntervalMillis) {
        Validate.notBlank(name, "name must not be empty");
        Validate.isTrue(maxDropCount > 0, "maxDropCount must be greater than 0");
        Validate.isTrue(drainIntervalMillis > 0, "drainIntervalMillis must be greater than 0");

        this.name = name;
        this.maxDropCount = maxDropCount;
        this.overflow = this.maxDropCount + 1;
        this.drainIntervalMillis = drainIntervalMillis;

        this.lastDrainedMillis = System.currentTimeMillis();
        this.lastUsedMillis = this.lastDrainedMillis;
    }

    /**
     * Invoke this method to add a drop to this bucket.
     *
     * @return maxDropCount + 1 when bucket is full, number of drops within drain interval otherwise
     */
    public synchronized long addDrop() {
        this.lastUsedMillis = System.currentTimeMillis();

        final long currentDrops;
        if (!isFull()) {
            currentDrops = ++this.dropCount;
            LOGGER.debug("added drop: {}", this);
        } else {
            this.leakedCount++;
            LOGGER.debug("leaked drop: {}", this);
            currentDrops = this.overflow;
        }
        return currentDrops;
    }

    /**
     * Invoke this method to request that all the drops are drained from this bucket.
     *
     * @return NOTHING_DRAINED when drain invocation got executed within the drainIntervalMillis,
     * number of drained drops otherwise
     */
    public synchronized long drain() {
        final long currentTimeMillis = System.currentTimeMillis();
        final long drained;
        if (currentTimeMillis - lastDrainedMillis < drainIntervalMillis) {
            LOGGER.debug("not drained: {}", this);
            drained = NOTHING_DRAINED;
        } else {
            this.lastDrainedMillis = currentTimeMillis;
            if (this.leakedCount > 0) {
                LOGGER.warn("{} dropped {} requests in {}ms", name, leakedCount, drainIntervalMillis);
            }
            drained = this.dropCount;
            this.dropCount = 0;
            this.leakedCount = 0;
            LOGGER.debug("drained: {}", this);
        }
        return drained;
    }

    public long getDrainIntervalMillis() {
        return this.drainIntervalMillis;
    }

    public long getLastUsedMillis() {
        return this.lastUsedMillis;
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
                + this.drainIntervalMillis + "ms " + super.toString();
    }

    protected long getDropCount() {
        return this.dropCount;
    }

    protected boolean isFull() {
        return this.dropCount == this.maxDropCount;
    }
}
