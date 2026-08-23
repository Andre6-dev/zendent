package com.zendent.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TypedIdTest {

	@Test
	void twoIdentifiersOverTheSameUuidAreNotInterchangeable() {
		UUID raw = UUID.randomUUID();

		assertThat(new ClinicId(raw)).isNotEqualTo(new UserId(raw));
		assertThat(new ClinicId(raw)).isEqualTo(new ClinicId(raw));
	}

	@Test
	void readsAndPrintsTheUnderlyingValue() {
		UUID raw = UUID.randomUUID();

		assertThat(ClinicId.of(raw.toString()).value()).isEqualTo(raw);
		assertThat(UserId.of(raw.toString())).hasToString(raw.toString());
	}

	@Test
	void refusesToWrapNothing() {
		assertThatThrownBy(() -> new ClinicId(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
	}

}
