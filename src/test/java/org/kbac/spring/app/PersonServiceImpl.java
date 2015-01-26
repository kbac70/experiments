package org.kbac.spring.app;

import org.kbac.spring.app.dao.PersonDAO;
import org.kbac.spring.app.entities.Person;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 */
@Service
public class PersonServiceImpl implements PersonService, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Autowired
    private PersonDAO userDAO;

    @Override
    public List<Person> getPersons() {
        return userDAO.getPersonsLong();
    }

    @Override
    @Cacheable(value = "persons", key = "#id")
    public List<Person> getPersonsCachedGlobally(int id) {
        return getPersons();
    }

    @Override
    @Cacheable(cacheManager = "scopedCacheManager", value = "default", key = "#id")
    public List<Person> getPersonsCachedLocally(int id) {
        return getPersons();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
