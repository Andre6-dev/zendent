package com.zendent.shared.domain;

/**
 * The messages the API returns to callers.
 *
 * <p>They live here rather than at each throw site because two callers
 * rejecting the same condition must not describe it two different ways, and
 * because a message the API returns is part of its contract.
 *
 * <p>Authentication failures stay deliberately uninformative: one message
 * covers an unknown email, a wrong password, and a Membership in another
 * Clinic, so that a failed attempt reveals nothing about which accounts exist.
 */
public final class ErrorMessages {

	public static final String VALIDATION_FAILED = "Validation failed";

	public static final String INVALID_CREDENTIALS = "Invalid credentials";

	public static final String ACCESS_DENIED = "Access denied";

	public static final String RESOURCE_CONFLICT = "Resource conflict";

	public static final String UNEXPECTED_ERROR = "An unexpected error occurred";

	public static final String UNKNOWN_CLINIC_ADDRESS = "Unknown Clinic address";

	public static final String REGISTRATION_REQUIRES_ONBOARDING_HOST =
			"Clinic registration is only available on the onboarding host";

	public static final String ALREADY_A_MEMBER = "That person already holds a Membership in this Clinic";

	public static final String INVITATION_NOT_REDEEMABLE = "No redeemable invitation for that token";

	public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

	public static final String MEMBER_NOT_FOUND = "No such member in this Clinic";

	public static final String CLINIC_MISMATCH =
			"This session belongs to a different Clinic";

	public static final String LOGIN_REQUIRES_CLINIC_HOST =
			"Login is only available on a Clinic's subdomain";

	public static final String PASSWORD_RESET_REQUIRES_CLINIC_HOST =
			"Password reset is only available on a Clinic's subdomain";

	public static final String PASSWORD_RESET_REQUEST_ACCEPTED =
			"If an account exists for that email, a password reset link will be sent.";

	private ErrorMessages() {
	}

}
