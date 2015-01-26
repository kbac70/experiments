package org.kbac.spring.app;

import org.kbac.spring.app.entities.Counter;
import org.springframework.context.ApplicationContext;

/**
 * @author Krzysztof Bacalski
 *
 * @since 2015-01-17
 */
public interface CounterService {

    ApplicationContext getApplicationContext();

    Counter getCounter(String name);

    Counter getCounterCachedGlobally(String name);

    Counter getCounterCachedLocally(String name);

    void clearCaches();
}
