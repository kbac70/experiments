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

import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by krzysztof on 16/01/2016.
 * <p/>
 * Runnable extension allowing to schedule bucket drain invocations
 *
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 *
 * @see LeakyBucket
 */
public class FixedTimeTimerTask extends TimerTask {

    public static final String IDLE_BUCKET_REMOVE_MILLIS_PROP_NAME = FixedTimeTimerTask.class.getCanonicalName() + ".removeIdleBucketsMillis";

    public static final long IDLE_BUCKET_REMOVE_MILLIS = 3000;

    public static final long IDLE_BUCKET_REMOVE_MILLIS_DEFAULT = Long.valueOf(
            System.getProperty(IDLE_BUCKET_REMOVE_MILLIS_PROP_NAME, "" + IDLE_BUCKET_REMOVE_MILLIS));

    private static final Logger LOGGER = LoggerFactory.getLogger(FixedTimeTimerTask.class);

    private final ConcurrentMap<String, ? extends LeakyBucket> buckets;

    private final long idleBucketRemoveIntervalMillis;


    public FixedTimeTimerTask(final ConcurrentMap<String, ? extends LeakyBucket> buckets) {
        this(buckets, IDLE_BUCKET_REMOVE_MILLIS_DEFAULT);
    }

    public FixedTimeTimerTask(final ConcurrentMap<String, ? extends LeakyBucket> buckets, final long idleBucketRemoveIntervalMillis) {
        Validate.notNull(buckets, "bucket container must not be null");
        Validate.isTrue(idleBucketRemoveIntervalMillis > 0, "idle interval must be greater than zero");

        this.buckets = buckets;
        this.idleBucketRemoveIntervalMillis = idleBucketRemoveIntervalMillis;
    }


    @Override
    public void run() {
        final Iterator<? extends Map.Entry<String, ? extends LeakyBucket>> bucketsIterator = buckets.entrySet().iterator();
        while (bucketsIterator.hasNext()) {
            final Map.Entry<String, ? extends LeakyBucket> entry = bucketsIterator.next();
            final LeakyBucket bucket = entry.getValue();
            final long drainedDropCount = bucket.drain();
            final long idleMillis = System.currentTimeMillis() - bucket.getLastUsedMillis();
            if (drainedDropCount == 0 && idleMillis >= this.idleBucketRemoveIntervalMillis) {
                bucketsIterator.remove();
                LOGGER.debug("removed unused bucket: {} idle for {}ms", bucket, idleMillis);
            }
        }
    }
}
