package org.kbac.spring.app;

import org.kbac.spring.app.entities.Counter;
import org.springframework.beans.BeansException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2015-01-17
 */
@Service
public class CounterServiceImpl implements CounterService, ApplicationContextAware {

    public static final String CACHE_NAME = "counters";
    public static final String SCOPED_CACHE_MANAGER = "scopedCacheManager";

    private ApplicationContext applicationContext;

    @Override
    public ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Counter getCounter(String name) {
        return applicationContext.getBean(name, Counter.class);
    }

    private Counter getCounterSlowly(String name) {
        Counter c = getCounter(name);
        c.getId();
        return c;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#name") // using default cacheManager
    public Counter getCounterCachedGlobally(String name) {
        return getCounterSlowly(name);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#name", cacheManager = SCOPED_CACHE_MANAGER)
    public Counter getCounterCachedLocally(String name) {
        return getCounterSlowly(name);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    @CacheEvict(value = "counters", allEntries = true)
    public void clearCaches() {
        //NOP
    }
}
