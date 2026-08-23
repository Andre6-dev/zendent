package com.zendent.iam.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * A long-lived credential that buys a new access token. Tenant-owned, so it
 * resolves only inside the Clinic that issued it: a token lifted from one
 * Clinic cannot be found from another, rather than being found and rejected.
 *
 * <p>Only the hash is stored. A database read — a backup, a support query, a
 * leaked dump — must not yield anything that can be presented as a credential.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

	@Id
	private UUID id;

	@TenantId
	@Column(name = "clinic_id", nullable = false)
	private UUID clinicId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(nullable = false)
	private String jti;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "rotated_from")
	private UUID rotatedFrom;

	protected RefreshToken() {
	}

	public RefreshToken(UUID clinicId, UUID userId, String tokenHash, String jti, Instant expiresAt,
			UUID rotatedFrom) {
		this.id = UUID.randomUUID();
		this.clinicId = clinicId;
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.jti = jti;
		this.issuedAt = Instant.now();
		this.expiresAt = expiresAt;
		this.rotatedFrom = rotatedFrom;
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	public Instant revokedAt() {
		return revokedAt;
	}

	public boolean isSpent() {
		return revokedAt != null;
	}

	public boolean hasExpiredBy(Instant moment) {
		return !expiresAt.isAfter(moment);
	}

	public boolean wasIssuedOnOrBefore(Instant moment) {
		return !issuedAt.isAfter(moment);
	}

	public void revoke(Instant moment) {
		if (revokedAt == null) {
			revokedAt = moment;
		}
	}

}
