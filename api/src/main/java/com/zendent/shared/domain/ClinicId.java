package com.zendent.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record ClinicId(UUID value) implements TypedId {

	public ClinicId {
		Objects.requireNonNull(value, "value must not be null");
	}

	public static ClinicId of(String value) {
		return new ClinicId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
