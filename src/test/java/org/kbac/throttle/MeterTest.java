package org.kbac.throttle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2016-01-17
 */
public class MeterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeterTest.class);

    Meter meter;

    @Before
    public void setUp() throws Exception {
        this.meter = new Meter(new LoggingThrottleStrategy(), Meter.THROTTLE_INTERVAL_MILLIS_DEFAULT, new ConcurrentHashMap<>());
    }

    @After
    public void tearDown() throws Exception {
        this.meter.close();
    }


    @Test
    public void noTimerWhenMapPassedInCtor() {
        assertNull("timer", this.meter.getTimer());
    }

    @Test
    public void throttlesRequestsOnExceedingMaxInvocationCount() throws Exception {
        final int MAX_REQUESTS = BucketUtils.MAX_DROP_COUNT;
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertFalse("request should NOT be throttled #" + i, meter.shouldThrottle(BucketUtils.BUCKET_NAME, MAX_REQUESTS));
        }

        assertTrue("request should be throttled", meter.shouldThrottle(BucketUtils.BUCKET_NAME, MAX_REQUESTS));
    }

    /**
     * Emulates timer invokable N times when put into CyclicBarrier
     */
    private static class CountedFixedTimeTimerTask extends FixedTimeTimerTask {

        final AtomicBoolean shouldContinue;

        final long maxCounter;

        int counter;

        public CountedFixedTimeTimerTask(ConcurrentMap<String, LeakyBucket> buckets, final AtomicBoolean shouldContinue
                , final long maxCounter) {
            super(buckets);
            this.shouldContinue = shouldContinue;
            this.maxCounter = maxCounter;
            this.counter = 1;
        }

        @Override
        public void run() {
            if (++this.counter > this.maxCounter) {
                this.shouldContinue.set(false);
                LOGGER.debug("end of draining buckets after {}", maxCounter);
            } else {
                try {
                    Thread.sleep(LeakyBucket.DEFAULT_DRAIN_INTERVAL_MILLIS_DEFAULT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                LOGGER.debug("draining buckets #{}, of {}", counter, maxCounter);
                super.run();
            }
        }
    }

    private Callable<TaskResult> newTask(final TaskDef task, final CyclicBarrier barrier, final Meter meter, final AtomicBoolean shouldContinue) {
        return new Callable<TaskResult>() {
            @Override
            public TaskResult call() throws Exception {

                final TaskResult result = new TaskResult();
                result.tasks.add(task);

                while(shouldContinue.get()) {

                    for (int i = 0; i < task.invocationCount; i++) {
                        if (meter.shouldThrottle(task.name, task.maxInvocations)) {
                            result.throttled.incrementAndGet();
                        } else {
                            result.passed.incrementAndGet();
                        }
                    }

                    barrier.await();
                }

                return result;
            }
        };
    }

    private static final class TaskDef {
        final String name;
        final long invocationCount;
        final long maxInvocations;

        TaskDef(String name, long invocationCount, long maxInvocations) {
            this.name = name;
            this.invocationCount = invocationCount;
            this.maxInvocations = maxInvocations;
        }

        @Override
        public String toString() {
            return "TaskDef{" +
                    "name='" + name + '\'' +
                    ", invocationCount=" + invocationCount +
                    ", maxInvocations=" + maxInvocations +
                    '}';
        }
    }

    private static final class TaskResult {
        final AtomicLong passed = new AtomicLong(0);
        final AtomicLong throttled = new AtomicLong(0);

        private final List<TaskDef> tasks;

        TaskResult() {
            this.tasks = new ArrayList<>();
        }

        public String getName() {
            return this.tasks.get(0).name;
        }

        public void addTask(TaskDef task) {
            if (!tasks.isEmpty()) {
                assertEquals("cannot add different task", this.getName(), task.name);
                assertEquals("same task aggregation not allows different limits", this.getMaxInvocationCount(), task.maxInvocations);
            }
            this.tasks.add(task);
        }

        public long getMaxInvocationCount() {
            return this.tasks.get(0).maxInvocations;
        }

        @Override
        public String toString() {
            return "TaskResult{" +
                    "passed=" + passed +
                    ", throttled=" + throttled +
                    ", tasks=" + tasks +
                    '}';
        }
    }

    @Test
    public void executesMultithreaded() throws Exception {

        final TaskDef[] tasks = {
                  new TaskDef("#4:SHRD", 100, 200)
                , new TaskDef("#1:LEAK", 300, 200)
                , new TaskDef("#4:SHRD", 100, 200)
                , new TaskDef("#2:NORM", 200, 200)
                , new TaskDef("#4:SHRD", 100, 200)
                , new TaskDef("#4:SHRD", 100, 200)
        };

        final ConcurrentMap<String, LeakyBucket> buckets = new ConcurrentHashMap<>();
        final int THREAD_COUNT = tasks.length;
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final CompletionService<TaskResult> execution = new ExecutorCompletionService(executor);
        final AtomicBoolean latch = new AtomicBoolean(true);
        final long REPEAT_COUNT = 1;
        final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT, new CountedFixedTimeTimerTask(buckets, latch, REPEAT_COUNT));

        try (final Meter meter = new Meter(new LoggingThrottleStrategy(), Meter.THROTTLE_INTERVAL_MILLIS_DEFAULT, buckets)) {
            //given
            final Map<String, TaskResult> expectations = new HashMap<>();
            for (final TaskDef task : tasks) {
                defineExpectations(expectations, task);
                execution.submit(newTask(task, barrier, meter, latch));
            }

            //when
            final Map<String, TaskResult> results = new HashMap<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                final TaskResult threadResult = execution.take().get();
                final TaskResult accumulatedResult = results.get(threadResult.getName());
                if (accumulatedResult == null) {
                    results.put(threadResult.getName(), threadResult);
                } else {
                    accumulatedResult.throttled.addAndGet(threadResult.throttled.get());
                    accumulatedResult.passed.addAndGet(threadResult.passed.get());
                }
            }

            //then
            for(final Map.Entry<String, TaskResult> actual : results.entrySet()) {
                final TaskResult expected = expectations.get(actual.getKey());
                final long actualPassed =  actual.getValue().passed.get() / REPEAT_COUNT;
                assertEquals("number of passed request for: " + expected.getName()
                        , expected.passed.get(), actualPassed);
                final long actualThrottled = actual.getValue().throttled.get() / REPEAT_COUNT;
                assertEquals("number of throttled requests for: " + expected.getName()
                        , expected.throttled.get(), actualThrottled);

                LOGGER.info("verified ok: {}", expected);
            }

        } finally {
            executor.shutdown();
        }
    }

    static final class RequestCounts {
        private long passed;
        private long throttled;

        private final long maxRequests;

        RequestCounts(TaskResult expectedResult) {
            this.maxRequests = expectedResult.getMaxInvocationCount();
            addPassed(expectedResult.passed.get());
            addThrottled(this.throttled + expectedResult.throttled.get());
        }

        RequestCounts(TaskDef task) {
            this.maxRequests = task.maxInvocations;
            addTask(task);
        }

        public long getPassed() {
            return passed;
        }

        public void addTask(TaskDef task) {
            assertEquals("same task aggregation not allows different limits", this.maxRequests, task.maxInvocations);
            addPassed(task.invocationCount);
        }

        public void addPassed(long passed) {
            final long newPassed = this.passed + passed;
            if (newPassed > this.maxRequests) {
                this.passed = this.maxRequests;
                this.addThrottled(newPassed - this.maxRequests);
            } else {
                this.passed += passed;
            }
        }

        public long getThrottled() {
            return throttled;
        }

        public void addThrottled(long throttled) {
            this.throttled += throttled;
        }

        public long getMaxRequests() {
            return maxRequests;
        }
    }

    private void defineExpectations(Map<String, TaskResult> expected, TaskDef task) {
        final boolean aggregating = expected.containsKey(task.name);
        final TaskResult result =  aggregating ? expected.get(task.name) : new TaskResult();
        result.tasks.add(task);

        final RequestCounts counts;
        if (aggregating) {
            counts = new RequestCounts(result);
            counts.addTask(task);
        } else {
            expected.put(task.name, result);
            counts = new RequestCounts(task);
        }

        result.passed.set(counts.getPassed());
        result.throttled.set(counts.getThrottled());
    }
}