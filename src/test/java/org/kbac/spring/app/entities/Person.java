package org.kbac.spring.app.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import static javax.persistence.GenerationType.IDENTITY;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "PERSON", schema = "PUBLIC", catalog = "PUBLIC")
public class Person implements java.io.Serializable {

	private Integer id;
	private String firstName;
	private String lastName;
	private boolean isDisabled;
	private boolean isDeleted;

	public Person() {
	}

	public Person(boolean isDisabled, boolean isDeleted) {
		this.isDisabled = isDisabled;
		this.isDeleted = isDeleted;
	}

	public Person(String firstName, String lastName, boolean isDisabled, boolean isDeleted) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.isDisabled = isDisabled;
		this.isDeleted = isDeleted;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "ID", unique = true, nullable = false)
	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Column(name = "FIRST_NAME", length = 128)
	public String getFirstName() {
		return this.firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	@Column(name = "LAST_NAME", length = 128)
	public String getLastName() {
		return this.lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	@Column(name = "IS_DISABLED", nullable = false)
	public boolean isIsDisabled() {
		return this.isDisabled;
	}

	public void setIsDisabled(boolean isDisabled) {
		this.isDisabled = isDisabled;
	}

	@Column(name = "IS_DELETED", nullable = false)
	public boolean isIsDeleted() {
		return this.isDeleted;
	}

	public void setIsDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}
}
