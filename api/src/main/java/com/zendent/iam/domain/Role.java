package com.zendent.iam.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "role")
public class Role {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, unique = true)
	private Code code;

	@Column(nullable = false)
	private String name;

	protected Role() {
	}

	public UUID id() {
		return id;
	}

	public enum Code {
		ADMIN,
		DENTIST,
		STAFF
	}

}
