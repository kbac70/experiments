package org.kbac.spring.app;

import org.kbac.spring.app.entities.Person;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 *
 */
public interface PersonService {

    ApplicationContext getApplicationContext();

    List<Person> getPersons();

    List<Person> getPersonsCachedGlobally(int id);

    List<Person> getPersonsCachedLocally(int id);
}
