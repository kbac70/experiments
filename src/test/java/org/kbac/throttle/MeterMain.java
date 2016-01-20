package org.kbac.throttle;

/**
 *
 * @author krzysztof
 */
public class MeterMain {

    public static void main(String[] args) throws Exception {
        
        final MeterTest test = new MeterTest();
        try {
            test.setUp();
            test.executesMultithreaded();
        } finally {
            test.tearDown();
        }
        
    }
}
