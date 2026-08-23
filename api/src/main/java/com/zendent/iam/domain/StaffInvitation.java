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

/**
 * An offer of Membership in a Clinic. Tenant-owned, so it resolves only inside
 * the Clinic that issued it: a token cannot be carried to another Clinic's
 * subdomain to gain a foothold there.
 *
 * <p>Redemption is public by necessity — the invited person has no session yet
 * — which makes the token the whole security boundary. It is therefore
 * single-use, expiring, and stored only as a hash.
 */
@Entity
@Table(name = "staff_invitation")
public class StaffInvitation {

	@Id
	private UUID id;

	@TenantId
	@Column(name = "clinic_id", nullable = false)
	private UUID clinicId;

	@Column(nullable = false)
	private String email;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(name = "invited_by", nullable = false)
	private UUID invitedBy;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	protected StaffInvitation() {
	}

	public StaffInvitation(UUID clinicId, String email, Role role, String tokenHash, UUID invitedBy,
			Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.clinicId = clinicId;
		this.email = email;
		this.role = role;
		this.tokenHash = tokenHash;
		this.status = Status.PENDING;
		this.invitedBy = invitedBy;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public UUID id() {
		return id;
	}

	public UUID clinicId() {
		return clinicId;
	}

	public String email() {
		return email;
	}

	public Role role() {
		return role;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	public boolean isRedeemableAt(Instant moment) {
		return status == Status.PENDING && expiresAt.isAfter(moment);
	}

	public void accept(Instant moment) {
		this.status = Status.ACCEPTED;
		this.acceptedAt = moment;
	}

	public enum Status {
		PENDING,
		ACCEPTED
	}

}
