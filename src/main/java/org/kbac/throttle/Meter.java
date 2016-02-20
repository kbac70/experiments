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

import java.util.Date;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by krzysztof on 21/12/2015.
 * <p/>
 * Thread safe controller allowing to throttle requests for a given name using pre-configured throttling strategy and interval
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class Meter implements AutoCloseable {

    public static final String THROTTLE_INTERVAL_PROP_NAME = Meter.class.getCanonicalName() + ".throttleInterval";

    public static final long THROTTLE_INTERVAL_MILLIS = 1000;

    public static final long THROTTLE_INTERVAL_MILLIS_DEFAULT = Long.valueOf(System.getProperty(
            THROTTLE_INTERVAL_PROP_NAME, "" + THROTTLE_INTERVAL_MILLIS));

    private static final Logger LOGGER = LoggerFactory.getLogger(Meter.class);

    private final ConcurrentMap<String, LeakyBucket> buckets;

    private final ThrottleStrategy throttleStrategy;

    private final long throttleIntervalMillis;

    private Timer timer;


    /**
     * Creates instance of a meter defaulting all its configuration (including Timer instance)
     */
    public Meter() {
        this(new LoggingThrottleStrategy());
    }


    /**
     * Creates instance of a meter defaulting all its configuration but throttleStrategy
     *
     * @param throttleStrategy
     * @see FixedTimeTimerTask
     */
    public Meter(final ThrottleStrategy throttleStrategy) {
        this(throttleStrategy, THROTTLE_INTERVAL_MILLIS_DEFAULT);
    }

    /**
     * Creates instance of a meter defaulting Timer only
     *
     * @param throttleStrategy
     * @param throttleIntervalMillis
     * @see FixedTimeTimerTask
     */
    public Meter(final ThrottleStrategy throttleStrategy, final long throttleIntervalMillis) {
        this(throttleStrategy, throttleIntervalMillis, new ConcurrentHashMap<>());

        this.timer = new Timer();
        this.timer.schedule(new FixedTimeTimerTask(this.buckets), new Date(), throttleIntervalMillis);

        LOGGER.info("{} using internal timer: {}ms", this, throttleIntervalMillis);
    }

    /**
     * Creates instance of the meter fully relying on external timer configuration operating over shared and
     * passed in buckets container
     *
     * @param throttleStrategy
     * @param throttleIntervalMillis
     * @param buckets
     * @see FixedTimeTimerTask as example of scheduled bucket drain invocations
     */
    public Meter(final ThrottleStrategy throttleStrategy, final long throttleIntervalMillis,
                 final ConcurrentMap<String, LeakyBucket> buckets) {
        Validate.notNull(throttleStrategy, "throttleStrategy must not be null");
        Validate.isTrue(throttleIntervalMillis > 0, "throttleIntervalMillis must be greater than zero");
        Validate.notNull(buckets, "bucket map must be provided");

        this.buckets = buckets;
        this.throttleStrategy = throttleStrategy;
        this.throttleIntervalMillis = throttleIntervalMillis;

        LOGGER.info("{} using throttle strategy: {} with interval: {}ms", this
                , this.throttleStrategy.getClass().getName(), this.throttleIntervalMillis);

    }

    /**
     * Invoke this method to decide if the request for a given name should be allowed to continue or throttled
     * as result of exceeding maxNumberOfRequests within pre-configured interval. The invocation is thread safe.
     *
     * @param name                of the request that should be throttled
     * @param maxNumberOfRequests defines maximum number of requests within pre-configured interval
     * @return true when the request should be rejected, false when it should continue
     * @throws TooManyRequestsException when pre-configured to use ThrowingThrottleStrategy
     * @see LoggingThrottleStrategy
     * @see ThrowingThrottleStrategy
     */
    public boolean shouldThrottle(final String name, final long maxNumberOfRequests) {
        LeakyBucket bucket = buckets.get(name);
        if (bucket == null) {
            bucket = new LeakyBucket(name, maxNumberOfRequests, throttleIntervalMillis);
            LeakyBucket prev = buckets.putIfAbsent(name, bucket);
            if (prev != null) {
                LOGGER.debug("previous bucket used {}", prev);
                bucket = prev;
            }
        }
        return throttleStrategy.dripAndCheckIfLeaked(bucket);
    }

    @Override
    public void close() throws Exception {
        if (this.timer != null) {
            this.timer.cancel();
            LOGGER.debug("timer cancelled for {}", this);
        }
        LOGGER.debug("{} is closed", this);
    }

    protected Timer getTimer() {
        return this.timer;
    }
}
