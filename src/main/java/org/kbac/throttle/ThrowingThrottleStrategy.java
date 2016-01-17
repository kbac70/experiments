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

/**
 * Created by krzysztof on 21/12/2015.
 * <p/>
 * {@inheritDoc}
 * <p/>
 * NB: This class might prove heavy use when large number of exceptions are being thrown due to heavy traffic that needs
 * to be throttled. As it will have impact on your JVM heap management.
 *
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class ThrowingThrottleStrategy extends LoggingThrottleStrategy {

    @Override
    protected void handleOverflowingBucket(final LeakyBucket bucket) {
        throw new TooManyRequestsException(String.format("Exceeded maximum number of requests {%d} per %dms for: %s"
                , bucket.getMaxDropCount(), bucket.getDrainIntervalMillis(), bucket.getName()));
    }
}
