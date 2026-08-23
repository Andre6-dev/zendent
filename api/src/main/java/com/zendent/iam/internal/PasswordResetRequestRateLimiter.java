package com.zendent.iam.internal;

import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.zendent.iam.domain.PasswordResetRequestLimit;

@Component
@EnableConfigurationProperties(PasswordResetRateLimitProperties.class)
class PasswordResetRequestRateLimiter {

	private static final String FINGERPRINT_DOMAIN = "zendent:password-reset-rate-limit:";

	private final EntityManager entityManager;
	private final PasswordResetRequestLimitRepository repository;
	private final KeyedDerivationPolicy keyedDerivationPolicy;
	private final PasswordResetRateLimitProperties properties;

	PasswordResetRequestRateLimiter(EntityManager entityManager, PasswordResetRequestLimitRepository repository,
			KeyedDerivationPolicy keyedDerivationPolicy, PasswordResetRateLimitProperties properties) {
		this.entityManager = entityManager;
		this.repository = repository;
		this.keyedDerivationPolicy = keyedDerivationPolicy;
		this.properties = properties;
	}

	boolean permit(UUID clinicId, String normalizedEmail) {
		String emailFingerprint = fingerprint(normalizedEmail);
		lock(clinicId + ":" + emailFingerprint);
		Instant now = Instant.now();
		return repository.findByEmailFingerprint(emailFingerprint)
				.map(limit -> limit.permit(now, properties.maxRequests(), properties.window()))
				.orElseGet(() -> createLimit(clinicId, emailFingerprint, now));
	}

	private boolean createLimit(UUID clinicId, String emailFingerprint, Instant now) {
		repository.save(new PasswordResetRequestLimit(clinicId, emailFingerprint, now));
		return true;
	}

	private void lock(String rateLimitKey) {
		entityManager.createNativeQuery("""
				SELECT pg_advisory_xact_lock(hashtextextended(CAST(:rateLimitKey AS text), 0))
				""")
				.setParameter("rateLimitKey", rateLimitKey)
				.getSingleResult();
	}

	private String fingerprint(String normalizedEmail) {
		return HexFormat.of().formatHex(keyedDerivationPolicy.derive(FINGERPRINT_DOMAIN, normalizedEmail));
	}

}
