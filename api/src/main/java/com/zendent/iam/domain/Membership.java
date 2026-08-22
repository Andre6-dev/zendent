package com.zendent.iam.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "membership")
public class Membership {

	@Id
	private UUID id;

	@TenantId
	@Column(name = "clinic_id", nullable = false)
	private UUID clinicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Membership() {
	}

	public Membership(UUID clinicId, User user, Role role) {
		this.id = UUID.randomUUID();
		this.clinicId = clinicId;
		this.user = user;
		this.role = role;
		this.status = Status.ACTIVE;
		this.createdAt = Instant.now();
	}

	public UUID id() {
		return id;
	}

	public UUID clinicId() {
		return clinicId;
	}

	public User user() {
		return user;
	}

	public Role role() {
		return role;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Status status() {
		return status;
	}

	public enum Status {
		ACTIVE
	}

}
