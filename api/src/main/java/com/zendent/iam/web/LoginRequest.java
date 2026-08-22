package com.zendent.iam.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries no Clinic: the subdomain already established it, and letting the
 * caller name their own Clinic here is the vulnerability ADR 0008 prevents.
 */
public record LoginRequest(
		@NotBlank @Size(max = 255) String email,
		@NotBlank @Size(max = 72) String password) {
}
