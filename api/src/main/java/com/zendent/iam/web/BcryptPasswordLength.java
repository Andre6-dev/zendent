package com.zendent.iam.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import com.zendent.shared.domain.ErrorMessages;

/** Keeps request validation aligned with BCrypt's 72-byte input limit. */
@Documented
@Constraint(validatedBy = BcryptPasswordLengthValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface BcryptPasswordLength {

	String message() default ErrorMessages.PASSWORD_EXCEEDS_BCRYPT_LIMIT;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
