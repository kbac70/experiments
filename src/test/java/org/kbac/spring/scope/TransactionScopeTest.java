package org.kbac.spring.scope;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2015-01-17
 */
@RunWith(MockitoJUnitRunner.class)
public class TransactionScopeTest {

    public static final String BEAN_NAME = "beanName";

    public static final String TX_NAME = "TransactionScope1";

    public static final Object bean1 = BEAN_NAME + 1;

    public static final Object bean2 = BEAN_NAME + 2;

    TransactionScope transactionScope;


    @Mock
    ObjectFactory objectFactory;


    @Before
    public void setUp() throws Exception {
        this.transactionScope = new TransactionScope();
    }

    @After
    public void tearDown() throws Exception {
        endTransaction();
    }

    @Test
    public void descopingIsolationLevelsDefaulted() {
        assertEquals(this.transactionScope.getDescopingIsolationLevels().size()
                , TransactionScope.DEFAULT_DESCOPING_ISOLATION_LEVELS.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getDisablingIsolationLevelsUnmodifiable() throws Exception {
        this.transactionScope.getDescopingIsolationLevels().add(1);
    }

    @Test
    public void descopingIsolationLevelsAssignmentWorking() throws Exception {
        this.transactionScope.setDescopingIsolationLevels(Collections.EMPTY_SET);
        assertTrue(this.transactionScope.getDescopingIsolationLevels().isEmpty());
    }

    private Object getBeanByName(String beanName) {
        return this.getBeanByName(beanName, this.transactionScope.getTransactionIds().iterator().next());
    }

    private Object getBeanByName(String beanName, String transactionName) {
        return this.transactionScope.getTransactionBeans(transactionName).get(beanName);
    }

    @Test
    public void getsPrototypeWhenNoTx() throws Exception {
        getPrototypeBean();
    }

    private Object getPrototypeBean() {
        final Object result = this.transactionScope.get(BEAN_NAME, objectFactory);
        assertTrue("transaction names should be empty", this.transactionScope.getTransactionIds().isEmpty());
        return result;
    }

    @Test
    public void getsPrototypeWhenOnDisablingIsolationLevel() {
        Set<Integer> includeReadCommitted = new HashSet<Integer>(TransactionScope.DEFAULT_DESCOPING_ISOLATION_LEVELS);
        includeReadCommitted.add(Connection.TRANSACTION_READ_COMMITTED);
        this.transactionScope.setDescopingIsolationLevels(includeReadCommitted);

        startTransaction();

        getPrototypeBean();
    }


    private void startTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionName(TX_NAME);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }

        assertTrue("transaction name must not be cached", this.transactionScope.getTransactionIds().isEmpty());
        assertEquals("unexpected current transaction name"
                , TX_NAME, TransactionSynchronizationManager.getCurrentTransactionName());
        assertEquals("unexpected transaction isolation level"
                , Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED), TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
        assertTrue("transaction synchronisations should be empty"
                , TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    private void endTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionName(null);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {

            final boolean READ_WRITE = false;
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.beforeCommit(READ_WRITE);
                sync.afterCommit();
            }

            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void getsBeanScopedToCurrentTx() {
        when(this.objectFactory.getObject()).thenReturn(bean1);

        startTransaction();

        final Object bean = this.transactionScope.get(BEAN_NAME, this.objectFactory);

        assertEquals("unexpected number of synchronisations"
                , 1, TransactionSynchronizationManager.getSynchronizations().size());
        assertEquals("unexpected number of cached transactions"
                , 1, this.transactionScope.getTransactionIds().size());
        assertEquals("unexpected number of cached beans"
                , 1, transactionScope.getTransactionBeans(transactionScope.getTransactionIds().iterator().next()).size());
        assertSame("bean not scoped to current transaction", bean1, getBeanByName(BEAN_NAME));
    }

    @Test
    public void removesNopWhenNoTx() throws Exception {
        getPrototypeBean();

        this.transactionScope.remove(BEAN_NAME);
        assertTrue("transaction names should be empty", this.transactionScope.getTransactionIds().isEmpty());
    }

    @Test
    public void removesScopedBeanInCurrentTx() {
        getsBeanScopedToCurrentTx();

        final Object removed = this.transactionScope.remove(BEAN_NAME);

        assertEquals("unexpected number of synchronisations"
                , 1, TransactionSynchronizationManager.getSynchronizations().size());
        assertEquals("unexpected number of cached transactions"
                , 1, this.transactionScope.getTransactionIds().size());
        assertEquals("unexpected number of cached beans"
                , 0, transactionScope.getTransactionBeans(transactionScope.getTransactionIds().iterator().next()).size());
        assertSame("unknown bean removed from current transaction scope", bean1, removed);
    }

    @Test
    public void txEndRemovesScopedBeans() {
        getsBeanScopedToCurrentTx();

        endTransaction();

        assertTrue("transaction name must not be cached", this.transactionScope.getTransactionIds().isEmpty());
    }

    @Test
    public void nullConversationIdWhenNoTx() throws Exception {
        assertNull(this.transactionScope.getConversationId());
    }

    @Test
    public void txNameConversationIdOnTx() throws Exception {
        getsBeanScopedToCurrentTx();
        assertEquals("unexpected transaction name"
                , transactionScope.getTransactionIds().iterator().next(), this.transactionScope.getConversationId());
    }


}