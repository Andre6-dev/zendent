package com.zendent.iam.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClinicRegistrationRequest(
		@NotBlank @Size(max = 255) String clinicName,
		@NotBlank @Size(max = 63) @Pattern(
				regexp = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?",
				message = "must be a lowercase subdomain label") String slug,
		@NotBlank @Email @Size(max = 255) String adminEmail,
		@NotBlank @Size(min = 12, max = 72) String adminPassword,
		@NotBlank @Size(max = 255) String adminFullName) {
}
