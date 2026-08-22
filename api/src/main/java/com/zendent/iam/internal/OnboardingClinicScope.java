package com.zendent.iam.internal;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.zendent.shared.tenancy.TenantContext;

/**
 * Bounded exception for Clinic onboarding: it activates only the Clinic that
 * the current transaction has just created. Normal requests must obtain their
 * Clinic from the subdomain or a validated JWT instead.
 */
@Component
final class OnboardingClinicScope {

	private final JdbcTemplate jdbcTemplate;

	OnboardingClinicScope(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	<T> T fromNewClinic(UUID clinicId, Supplier<T> action) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Onboarding Clinic scope requires an active transaction");
		}
		if (TenantContext.get().isPresent()) {
			throw new IllegalStateException("Clinic registration must start without an active Clinic");
		}

		TenantContext.set(clinicId);
		try {
			jdbcTemplate.queryForObject(
					"SELECT set_config('app.clinic_id', ?, true)", String.class, clinicId.toString());
			return Objects.requireNonNull(action, "action must not be null").get();
		}
		finally {
			TenantContext.clear();
		}
	}

}
