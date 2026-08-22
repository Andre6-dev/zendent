package com.zendent.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	@Test
	void activeClinicCanBeSetReadAndCleared() {
		UUID clinicId = UUID.randomUUID();

		TenantContext.set(clinicId);

		assertThat(TenantContext.get()).contains(clinicId);

		TenantContext.clear();

		assertThat(TenantContext.get()).isEmpty();
	}

	@Test
	void anotherThreadCannotObserveTheActiveClinic() throws Exception {
		TenantContext.set(UUID.randomUUID());

		try (var executor = Executors.newSingleThreadExecutor()) {
			assertThat(executor.submit(TenantContext::get).get()).isEmpty();
		}
	}

	@Test
	void scopedClinicIsClearedWhenWorkThrows() {
		UUID clinicId = UUID.randomUUID();

		assertThatThrownBy(() -> TenantContext.withClinic(clinicId, () -> {
			assertThat(TenantContext.get()).contains(clinicId);
			throw new IllegalStateException("transaction failed");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(TenantContext.get()).isEmpty();
	}

}
