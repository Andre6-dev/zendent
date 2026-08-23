package com.zendent.iam.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

/** The current fixed-window request count for one address in one Clinic. */
@Entity
@Table(name = "password_reset_request_limit", uniqueConstraints =
		@UniqueConstraint(columnNames = { "clinic_id", "email_fingerprint" }))
public class PasswordResetRequestLimit {

	@Id
	private UUID id;

	@TenantId
	@Column(name = "clinic_id", nullable = false)
	private UUID clinicId;

	@Column(name = "email_fingerprint", nullable = false, length = 64)
	private String emailFingerprint;

	@Column(name = "window_started_at", nullable = false)
	private Instant windowStartedAt;

	@Column(name = "request_count", nullable = false)
	private int requestCount;

	protected PasswordResetRequestLimit() {
	}

	public PasswordResetRequestLimit(UUID clinicId, String emailFingerprint, Instant firstRequestAt) {
		this.id = UUID.randomUUID();
		this.clinicId = clinicId;
		this.emailFingerprint = emailFingerprint;
		this.windowStartedAt = firstRequestAt;
		this.requestCount = 1;
	}

	public boolean permit(Instant requestedAt, int maxRequests, Duration window) {
		if (!windowStartedAt.isAfter(requestedAt.minus(window))) {
			windowStartedAt = requestedAt;
			requestCount = 1;
			return true;
		}
		if (requestCount >= maxRequests) {
			return false;
		}
		requestCount++;
		return true;
	}

}
