package org.kbac.spring.scope;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kbac.spring.app.CounterService;
import org.kbac.spring.app.entities.Counter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2015-01-17
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
         "classpath:spring-app-config.xml"
        ,"classpath:spring-tx-config.xml"
        ,"classpath:spring-cache-config.xml"
})
public class TransactionScopeIntegrationTest {

    private static final Log logger = LogFactory.getLog(TransactionScopeIntegrationTest.class);

    @Autowired
    private CounterService counterService;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    private TransactionScope txScope;


    private <F extends Predicate<A>, A> long timeOf(F func, A arg, String ... msg) {
        final long start = System.currentTimeMillis();
        assertTrue(msg == null || msg.length == 0 ? "unexpected predicate value for " + func : msg[0], func.test(arg));
        final long elapsed = System.currentTimeMillis() - start;
        logger.debug("elapsed time for [" + arg + "] is " + elapsed + "ms");
        return elapsed;
    }

    private <T> T doInTx(final TransactionCallback<T> callback) {
        return txTemplate.execute(callback);
    }
    

    @Before
    public void setUp() {
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    private String getScopesFirstTxId() {
        assertFalse("tx scopes transactionIds must not be empty", txScope.getTransactionIds().isEmpty());
        return txScope.getTransactionIds().iterator().next();
    }

    @Test
    public void singletonCounterIsASingleton() {
        Counter c1, c2;

        c1 = counterService.getCounter(Counter.SINGLETON_COUNTER);
        c2 = counterService.getCounter(Counter.SINGLETON_COUNTER);

        assertSame("counter instances should refer to a singleton", c1, c2);
        assertEquals("scope's cache should be empty", 0, txScope.getTransactionIds().size());
    }

    @Test
    public void prototypeCounterIsAPrototype() {
        Counter c1, c2;

        c1 = counterService.getCounter(Counter.PROTOTYPE_COUNTER);
        c2 = counterService.getCounter(Counter.PROTOTYPE_COUNTER);

        assertNotSame("counter instances should be prototypes", c1, c2);
        assertEquals("scope's cache should be empty", 0, txScope.getTransactionIds().size());
    }

    @Test
    public void scopedCounterInstanceChangesOnTx() {
        Counter c1, c2;

        c1 = counterService.getCounter(Counter.SCOPED_COUNTER);
        c2 = counterService.getCounter(Counter.SCOPED_COUNTER);

        assertNotSame("should return different counter instance in different tx", c1, c2);
        assertEquals("scope's cache should be empty", 0, txScope.getTransactionIds().size());
    }

    @Test
    public void scopedCounterInScopesCacheAndSameInSameTx() {
        doInTx(status -> {
            Counter c1, c2;

            c1 = counterService.getCounter(Counter.SCOPED_COUNTER);
            c2 = counterService.getCounter(Counter.SCOPED_COUNTER);

            assertSame("should return same counter scoped in one tx", c1, c2);
            assertEquals("scope's cache must not be empty", 1, txScope.getTransactionIds().size());
            assertTrue("scope's cache must contain key " + Counter.SCOPED_COUNTER
                    , txScope.getTransactionBeans(getScopesFirstTxId()).containsKey(Counter.SCOPED_COUNTER));
            assertTrue("scope's cache must contain value " + c1
                    , txScope.getTransactionBeans(getScopesFirstTxId()).containsValue(c1));

            return true;
        });
        assertEquals("scope's cache should be empty", 0, txScope.getTransactionIds().size());
    }

    @Test
    public void scopesCacheClearedOnRollback() {
        doInTx(status -> {
            counterService.getCounter(Counter.SCOPED_COUNTER);
            assertEquals("scope's cache must not be empty", 1, txScope.getTransactionIds().size());

            status.setRollbackOnly();

            return true;
        });
        assertEquals("scope's cache should be empty", 0, txScope.getTransactionIds().size());
    }

    @Test
    public void subsequentGloballyCachedInvocationsAreFaster() {
        final long firstInvocation = timeOf((name) -> counterService.getCounterCachedGlobally(name) != null, Counter.SCOPED_COUNTER);
        final long secondInvocation = timeOf((name) -> counterService.getCounterCachedGlobally(name) != null, Counter.SCOPED_COUNTER);
        assertTrue("second invocation elapsed time should be greater than first !(" + firstInvocation + ">" + secondInvocation + ")"
                , secondInvocation < firstInvocation);
    }

    @Test
    public void subsequentLocallyCachedInvocationsOverSeparateTxAreRoughlySame() {
        final long firstInvocation = timeOf((name) -> counterService.getCounterCachedLocally(name) != null, Counter.SCOPED_COUNTER);
        final long secondInvocation = timeOf((name) -> counterService.getCounterCachedLocally(name) != null, Counter.SCOPED_COUNTER);
        assertTrue("no caching expected, 1st invocation took " + firstInvocation, firstInvocation >= Counter.COUNTER_WAIT_TIME);
        assertTrue("no caching expected, 2nd invocation took " + secondInvocation, secondInvocation >= Counter.COUNTER_WAIT_TIME);
    }

    @Test
    public void scopedCounterAvailableWhenCachedAfterRemovalFromScope() {
        doInTx(status -> {
            Counter c1, c2, c3;

            c1 = counterService.getCounterCachedLocally(Counter.SCOPED_COUNTER);
            c2 = (Counter) txScope.remove(Counter.SCOPED_COUNTER);
            assertFalse("scope's cache should be empty"
                    , txScope.getTransactionBeans(getScopesFirstTxId()).containsKey(Counter.SCOPED_COUNTER));

            c3 = counterService.getCounterCachedLocally(Counter.SCOPED_COUNTER);

            assertSame("should be same", c1, c2);
            assertSame("should be same", c2, c3);

            return true;
        });
        assertEquals("scope's cache should be empty", 0, txScope.getTransactionIds().size());
    }

    @Test
    public void subsequentLocallyCachedInvocationsOverSameTxAreFaster() {
        doInTx(status -> {
            final long firstInvocation = timeOf((name) -> counterService.getCounterCachedLocally(name) != null, Counter.SCOPED_COUNTER);
            final long secondInvocation = timeOf((name) -> counterService.getCounterCachedLocally(name) != null, Counter.SCOPED_COUNTER);
            assertTrue("second invocation should be cached !(" + secondInvocation + ">" + Counter.COUNTER_WAIT_TIME + ")"
                    , secondInvocation < Counter.COUNTER_WAIT_TIME);
            return true;
        });
    }

    @Test
    public void nestedNewTransactionsCachesOnSubsequentInvocation() {
        doInTx(status -> {
            final Counter c1 = counterService.getCounterCachedLocally(Counter.SCOPED_COUNTER);
            final long secondInvocation = timeOf((name) -> counterService.getCounterCachedLocally(name) == c1
                    , Counter.SCOPED_COUNTER
                    , "should return same counter instance as first invocation to getCounter in current tx");
            assertTrue("second invocation should be cached !(" + secondInvocation + ">" + Counter.COUNTER_WAIT_TIME + ")"
                    , secondInvocation < Counter.COUNTER_WAIT_TIME);

            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            doInTx(status2 -> {
                final Object[] tuple = {Counter.SCOPED_COUNTER, null};
                final long thirdInvocation = timeOf((objects) -> {
                        objects[1] = counterService.getCounterCachedLocally(objects[0].toString());
                        return objects[1] != c1;
                    }, tuple, "should return new counter instance in new tx");
                assertTrue("no caching expected, 3rd invocation took " + thirdInvocation, thirdInvocation >= Counter.COUNTER_WAIT_TIME);
                assertEquals("unexpected number of transactions", 2, txScope.getTransactionIds().size());
                final long fourthInvocation = timeOf((name) -> counterService.getCounterCachedLocally(name) == tuple[1]
                        , Counter.SCOPED_COUNTER
                        , "should return same counter instance as first invocation in nested tx");
                assertTrue("fourth invocation should be cached !(" + fourthInvocation + ">" + Counter.COUNTER_WAIT_TIME + ")"
                        , fourthInvocation < Counter.COUNTER_WAIT_TIME);

                return true;
            });

            assertEquals("unexpected number of transactions", 1, txScope.getTransactionIds().size());
            final long fifthInvocation = timeOf((name) -> counterService.getCounterCachedLocally(name) == c1
                    , Counter.SCOPED_COUNTER
                    , "should return same counter instance as first invocation to getCounter in current tx");
            assertTrue("fifth invocation should be cached !(" + fifthInvocation + ">" + Counter.COUNTER_WAIT_TIME + ")"
                    , fifthInvocation < Counter.COUNTER_WAIT_TIME);

            return true;
        });
        assertEquals("unexpected number of transactions", 0, txScope.getTransactionIds().size());
    }
}
