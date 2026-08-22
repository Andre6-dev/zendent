package com.zendent.iam.web;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zendent.iam.internal.ClinicLoginService;
import com.zendent.iam.internal.ClinicOnboardingService;
import com.zendent.iam.mapper.ClinicOnboardingMapper;
import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.tenancy.TenantContext;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Clinic onboarding and session issuance")
public class AuthController {

	private final ClinicOnboardingService onboardingService;
	private final ClinicLoginService loginService;
	private final ClinicOnboardingMapper mapper;

	AuthController(ClinicOnboardingService onboardingService, ClinicLoginService loginService,
			ClinicOnboardingMapper mapper) {
		this.onboardingService = onboardingService;
		this.loginService = loginService;
		this.mapper = mapper;
	}

	@PostMapping("/register")
	@Operation(summary = "Register a Clinic",
			description = "Creates a Clinic, its administrator, and that administrator's Membership. "
					+ "Available only on the onboarding host, where no Clinic is active.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Clinic registered"),
			@ApiResponse(responseCode = "400", description = "Invalid registration payload", content = @io.swagger.v3.oas.annotations.media.Content),
			@ApiResponse(responseCode = "403", description = "A Clinic is already active — this is not the onboarding host", content = @io.swagger.v3.oas.annotations.media.Content),
			@ApiResponse(responseCode = "409", description = "Clinic slug or administrator email already taken", content = @io.swagger.v3.oas.annotations.media.Content),
	})
	ResponseEntity<ClinicRegistrationResponse> register(@Valid @RequestBody ClinicRegistrationRequest request) {
		// Registration is the one flow that runs before its Clinic exists, so an
		// already-active Clinic means the request named one — by subdomain or by
		// the development override — and registration must never attach itself
		// to a Clinic the caller chose.
		if (TenantContext.get().isPresent()) {
			throw new AccessDeniedException(ErrorMessages.REGISTRATION_REQUIRES_ONBOARDING_HOST);
		}
		UUID clinicId = onboardingService.register(request);
		return ResponseEntity.created(URI.create("/clinics/" + clinicId))
				.body(mapper.toResponse(clinicId));
	}

	@PostMapping("/login")
	@Operation(summary = "Start a session on a Clinic's subdomain",
			description = "Authenticates against the Clinic the subdomain resolved to. The Membership lookup is "
					+ "scoped by the database, so a Membership in another Clinic cannot authenticate here.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Session issued"),
			@ApiResponse(responseCode = "400", description = "Invalid login payload", content = @io.swagger.v3.oas.annotations.media.Content),
			@ApiResponse(responseCode = "401", description = "Credentials rejected, or no Membership in this Clinic", content = @io.swagger.v3.oas.annotations.media.Content),
			@ApiResponse(responseCode = "403", description = "No Clinic is active — login needs a Clinic's subdomain", content = @io.swagger.v3.oas.annotations.media.Content),
			@ApiResponse(responseCode = "404", description = "The host names no Clinic", content = @io.swagger.v3.oas.annotations.media.Content),
	})
	LoginResponse login(@Valid @RequestBody LoginRequest request) {
		if (TenantContext.get().isEmpty()) {
			throw new AccessDeniedException(ErrorMessages.LOGIN_REQUIRES_CLINIC_HOST);
		}
		return loginService.authenticate(request.email(), request.password());
	}

}
