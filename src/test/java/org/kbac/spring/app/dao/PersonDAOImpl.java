package org.kbac.spring.app.dao;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.kbac.spring.app.entities.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 *
 */
@Repository
public class PersonDAOImpl implements PersonDAO {

    public static final int SLEEP_TIME = 200;

    private static final Log logger = LogFactory.getLog(PersonDAOImpl.class);

    @Autowired
    private SessionFactory sessionFactory;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Person> getPersons() {
        Session session = sessionFactory.getCurrentSession();
        Query query = session.createQuery("from Person p");
        return query.list();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Person> getPersonsLong() {
        sleep();
        return getPersons();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void addPerson(Person toAdd) {
        Session session = sessionFactory.getCurrentSession();
        session.save(toAdd);
    }

    private void sleep() {
        try {
            Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
            logger.error(e);
        }
    }
}
