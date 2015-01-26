package org.kbac.spring.app.entities;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by krzysztof on 21/01/15.
 */
public class Counter {

    public static final int COUNTER_WAIT_TIME = 200;

    public static final String SINGLETON_COUNTER = "counter";
    public static final String PROTOTYPE_COUNTER = "protoCounter";
    public static final String SCOPED_COUNTER = "scopedCounter";

    private static final AtomicInteger counter = new AtomicInteger();

    private int id;

    public Counter() {
        this.id = counter.incrementAndGet();
    }

    public int getId() {
        try {
            Thread.sleep(COUNTER_WAIT_TIME);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return id;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + id;
    }
}
