package com.zendent.shared.tenancy;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves Hibernate's Clinic discriminator. The sentinel is deliberately not
 * a root tenant: without an active Clinic, ORM reads match no real rows and
 * writes fail their foreign-key/RLS checks.
 */
@Component
public final class ClinicTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

	private static final UUID NO_ACTIVE_CLINIC = new UUID(0, 0);

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		return TenantContext.get().orElse(NO_ACTIVE_CLINIC);
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

}
