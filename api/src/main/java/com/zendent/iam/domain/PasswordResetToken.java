package com.zendent.iam.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/** A single-use credential reset secret, persisted only by its hash. */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

	@Id
	private UUID id;

	@TenantId
	@Column(name = "clinic_id", nullable = false)
	private UUID clinicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(UUID id, UUID clinicId, User user, String tokenHash) {
		this.id = id;
		this.clinicId = clinicId;
		this.user = user;
		this.tokenHash = tokenHash;
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

	public boolean isRedeemableAt(Instant moment, Duration timeToLive) {
		return usedAt == null && createdAt.plus(timeToLive).isAfter(moment);
	}

	public void markUsed(Instant moment) {
		usedAt = moment;
	}

}
