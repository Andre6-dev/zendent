package com.zendent.shared.tenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TenantContext {

	private static final ThreadLocal<UUID> ACTIVE_CLINIC = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(UUID clinicId) {
		ACTIVE_CLINIC.set(Objects.requireNonNull(clinicId, "clinicId must not be null"));
	}

	public static Optional<UUID> get() {
		return Optional.ofNullable(ACTIVE_CLINIC.get());
	}

	public static <T> T withClinic(UUID clinicId, Supplier<T> action) {
		set(clinicId);
		try {
			return Objects.requireNonNull(action, "action must not be null").get();
		}
		finally {
			clear();
		}
	}

	public static void clear() {
		ACTIVE_CLINIC.remove();
	}

}
