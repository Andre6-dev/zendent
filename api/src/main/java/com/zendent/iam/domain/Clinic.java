package com.zendent.iam.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic")
public class Clinic {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Clinic() {
	}

	public Clinic(String name, String slug) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.slug = slug;
		this.status = Status.ACTIVE;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;
	}

	public UUID id() {
		return id;
	}

	/** What the Clinic calls itself, as a person reads it. */
	public String name() {
		return name;
	}

	public String slug() {
		return slug;
	}

	public enum Status {
		ACTIVE
	}

}
