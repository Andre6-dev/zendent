package com.zendent.iam.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zendent.iam.internal.ClinicOnboardingService;
import com.zendent.iam.mapper.ClinicOnboardingMapper;
import com.zendent.shared.tenancy.TenantContext;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final ClinicOnboardingService onboardingService;
	private final ClinicOnboardingMapper mapper;

	AuthController(ClinicOnboardingService onboardingService, ClinicOnboardingMapper mapper) {
		this.onboardingService = onboardingService;
		this.mapper = mapper;
	}

	@PostMapping("/register")
	ResponseEntity<ClinicRegistrationResponse> register(@Valid @RequestBody ClinicRegistrationRequest request) {
		// Registration is the one flow that runs before its Clinic exists, so an
		// already-active Clinic means the request named one — by subdomain or by
		// the development override — and registration must never attach itself
		// to a Clinic the caller chose.
		if (TenantContext.get().isPresent()) {
			throw new AccessDeniedException("Clinic registration is only available on the onboarding host");
		}
		UUID clinicId = onboardingService.register(request);
		return ResponseEntity.created(URI.create("/clinics/" + clinicId))
				.body(mapper.toResponse(clinicId));
	}

}
