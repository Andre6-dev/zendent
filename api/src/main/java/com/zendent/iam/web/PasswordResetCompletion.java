package com.zendent.iam.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetCompletion(
		@NotBlank @Size(max = 255) String token,
		@NotBlank @Size(min = 12) @BcryptPasswordLength String newPassword) {
}
