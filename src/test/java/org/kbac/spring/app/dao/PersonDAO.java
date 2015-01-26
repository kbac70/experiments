package org.kbac.spring.app.dao;

import org.kbac.spring.app.entities.Person;

import java.util.List;

/**
 *
 */
public interface PersonDAO {

    List<Person> getPersons();

    List<Person> getPersonsLong();

    void addPerson(Person toAdd);
}
