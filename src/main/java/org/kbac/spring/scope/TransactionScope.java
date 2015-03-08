/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Krzysztof Bacalski
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
package org.kbac.spring.scope;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom scope implementation ensuring beans life cycle is coupled with the current transaction life cycle. When no
 * current transaction is defined the scope will produce prototype beans.
 *
 * This scope can be used e.g. to build up Cacheable beans for repeatable read within the current transaction context.
 *
 * NB: You can define a setup in which the coupling of the TransactionScope with the transaction can be abandoned.
 * Simply provide your required transaction isolation levels that will disable the scope and make the TransactionScope
 * produce prototype beans instead of caching them against the current transaction. For more
 * @see org.kbac.spring.scope.TransactionScope#setDescopingIsolationLevels
 *
 * @author Krzysztof Bacalski
 *
 * @since 2015-01-17
 *
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.beans.factory.config.Scope
 */
public class TransactionScope implements Scope {

    public static final Set<Integer> DEFAULT_DESCOPING_ISOLATION_LEVELS;

    static {
        Set<Integer> defaultDescopingIsolationLevels = new HashSet<Integer>();
        defaultDescopingIsolationLevels.add(Connection.TRANSACTION_NONE);
        defaultDescopingIsolationLevels.add(Connection.TRANSACTION_READ_UNCOMMITTED);
        defaultDescopingIsolationLevels.add(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
        DEFAULT_DESCOPING_ISOLATION_LEVELS = Collections.unmodifiableSet(defaultDescopingIsolationLevels);
    }

    private static final Log logger = LogFactory.getLog(TransactionScope.class);

    private final Map<String, Map<String, Object>> transactionNamedBeans = new ConcurrentHashMap<String, Map<String, Object>>();

    private final AtomicLong transactionCounter = new AtomicLong(0);

    private Set<Integer> descopingIsolationLevels = DEFAULT_DESCOPING_ISOLATION_LEVELS;


    private boolean inDescopingIsolationLevel() {
        final Integer currentTransactionIsolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        return descopingIsolationLevels.contains(currentTransactionIsolationLevel);
    }

    protected String formatTransactionId(String currentTxName) {
        currentTxName = currentTxName == null ? "" : currentTxName;
        return currentTxName
                + "@" + this.getClass().getSimpleName() + Thread.currentThread().getId()
                + "." + transactionCounter.getAndIncrement();
    }

    private String getCurrentTransactionId() {
        final String currentTransactionId;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            NamedTransactionSynchronisation currentSynchronisation = null;
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                if (sync instanceof NamedTransactionSynchronisation) {
                    currentSynchronisation = (NamedTransactionSynchronisation) sync;
                    break;
                }
            }

            if (currentSynchronisation != null) {
                currentTransactionId = currentSynchronisation.transactionId;
            } else {
                currentTransactionId = formatTransactionId(TransactionSynchronizationManager.getCurrentTransactionName());
            }
        } else {
            currentTransactionId = null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("using current transaction name [" + currentTransactionId + "]");
        }

        return currentTransactionId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(String beanName, ObjectFactory<?> factory) {
        final String currentTransactionId = getCurrentTransactionId();
        final boolean isDebugEnabled = logger.isDebugEnabled();

        if (inDescopingIsolationLevel() || currentTransactionId == null) {
            if (isDebugEnabled) {
                logger.debug("returning prototype bean for [" + beanName + "]");
            }
            return factory.getObject();
        }

        Map<String, Object> namedBeans = transactionNamedBeans.get(currentTransactionId);
        if (namedBeans == null) {
            namedBeans = new HashMap<String, Object>();
            transactionNamedBeans.put(currentTransactionId, namedBeans);
            TransactionSynchronizationManager.registerSynchronization(new NamedTransactionSynchronisation(currentTransactionId));
            if (isDebugEnabled) {
                logger.debug("created new cache for [" + currentTransactionId + "]");
            }
        }

        Object bean = namedBeans.get(beanName);
        if (bean == null) {
            bean = factory.getObject();
            final Object previousBean = namedBeans.put(beanName, bean);
            if (isDebugEnabled) {
                logger.debug("returning new bean added to cache [" + beanName + "]->[" + bean + "] replacing [" + previousBean + "]");
            }
        } else {
            if (isDebugEnabled) {
                logger.debug("returning cached bean [" + beanName + "]->[" + bean + "]");
            }
        }
        return bean;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object remove(String beanName) {
        final String currentTransactionName = getCurrentTransactionId();

        final Map<String, Object> beanNames = currentTransactionName == null ? null : transactionNamedBeans.get(currentTransactionName);
        final Object removedBean = beanNames == null ? null : beanNames.remove(beanName);
        if (logger.isDebugEnabled()) {
            logger.debug("removed bean [" + beanName + "]->[" + removedBean + "]");
        }
        return removedBean;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerDestructionCallback(String beanName, Runnable runnable) {
        //NOP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object resolveContextualObject(String beanName) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConversationId() {
        return getCurrentTransactionId();
    }

    /**
     * @return unmodifiable set of currently managed transaction Ids
     */
    public Set<String> getTransactionIds() {
        return Collections.unmodifiableSet(this.transactionNamedBeans.keySet());
    }

    /**
     * @param transactionId
     * @return unmodifiable map of bean names to bean instances for given transactionId, empty map when none found
     */
    public Map<String, Object> getTransactionBeans(String transactionId) {
        final Map<String, Object> namedBeans = this.transactionNamedBeans.get(transactionId);
        return Collections.unmodifiableMap(namedBeans == null ? Collections.EMPTY_MAP : namedBeans);
    }


    /**
     * @return unmodifiable set of transaction isolation levels that disable scope from coupling to the transaction
     * and making scope produce beans as prototypes
     *
     * @see org.springframework.transaction.support.TransactionSynchronizationManager#getCurrentTransactionIsolationLevel
     */
    public Set<Integer> getDescopingIsolationLevels() {
        return Collections.unmodifiableSet(descopingIsolationLevels);
    }

    /**
     * @param descopingIsolationLevels non null set of transaction isolation levels that disable scope from coupling
     * to transaction and making scope produce beans as prototypes
     *
     * @see org.springframework.transaction.support.TransactionSynchronizationManager#getCurrentTransactionIsolationLevel
     */
    public void setDescopingIsolationLevels(Set<Integer> descopingIsolationLevels) {
        Assert.notNull(descopingIsolationLevels);
        this.descopingIsolationLevels = descopingIsolationLevels;
    }

    /**
     * Helper class ensuring that the scope is coupled with the transaction. It removes beans from the cache upon the
     * transaction completion.
     */
    class NamedTransactionSynchronisation implements TransactionSynchronization {

        private final String transactionId;

        private final long startTimeInMillis;


        NamedTransactionSynchronisation(String transactionId) {
            Assert.notNull(transactionId);
            this.transactionId = transactionId;
            this.startTimeInMillis = System.currentTimeMillis();
        }

        @Override
        public void suspend() {
            //NOP
        }

        @Override
        public void resume() {
            //NOP
        }

        @Override
        public void flush() {
            //NOP
        }

        @Override
        public void beforeCommit(boolean b) {
            //NOP
        }

        @Override
        public void beforeCompletion() {
            //NOP
        }

        @Override
        public void afterCommit() {
            //NOP
        }

        @Override
        public void afterCompletion(int i) {
            removeTransactionBeans();
        }

        private void removeTransactionBeans() {
            final Map<String, Object> removedBeans = transactionNamedBeans.remove(this.transactionId);
            final long cacheElapsedTime = System.currentTimeMillis() - this.startTimeInMillis;

            if (removedBeans != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("dropping cache of " + removedBeans.size() + " beans held for " + cacheElapsedTime + "ms for transaction [" + transactionId + "]");
                }

                if (logger.isTraceEnabled()) {
                    Assert.notNull(removedBeans);
                    for (Map.Entry<String, Object> cacheEntry : removedBeans.entrySet()) {
                        logger.trace("removed [" + cacheEntry.getKey() + "]->[" + cacheEntry.getValue() + "]");
                    }
                }
            } else if (logger.isTraceEnabled()) {
                logger.trace("no beans to remove for transaction [" + transactionId + "]");
            }
        }
    }

}
