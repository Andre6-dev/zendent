package com.zendent.iam.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Both fields are always required, even when the invited person already has an
 * identity and they will be ignored. Asking only when the identity is new would
 * tell an unauthenticated caller which emails are registered.
 */
public record InvitationAcceptance(
		@NotBlank @Size(max = 255) String fullName,
		@NotBlank @Size(min = 12, max = 72) String password) {
}
