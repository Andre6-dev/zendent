package com.zendent.iam.web;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Measures bytes rather than Java characters, as BCrypt does. */
public class BcryptPasswordLengthValidator implements ConstraintValidator<BcryptPasswordLength, String> {

	private static final int MAX_BYTES = 72;

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
	}
}
