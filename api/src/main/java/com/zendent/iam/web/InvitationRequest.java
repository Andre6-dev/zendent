package com.zendent.iam.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.zendent.iam.domain.Role;

/**
 * Carries no Clinic: the invitation is always for the administrator's own,
 * which the session already establishes.
 */
public record InvitationRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotNull Role.Code role) {
}
