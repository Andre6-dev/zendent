package com.zendent.iam.web;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zendent.iam.internal.ClinicOnboardingService;
import com.zendent.iam.mapper.ClinicOnboardingMapper;
import com.zendent.shared.tenancy.ClinicHostClassifier;
import com.zendent.shared.tenancy.ClinicHostClassifier.HostKind;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final ClinicOnboardingService onboardingService;
	private final ClinicOnboardingMapper mapper;
	private final ClinicHostClassifier hostClassifier;

	AuthController(ClinicOnboardingService onboardingService, ClinicOnboardingMapper mapper,
			ClinicHostClassifier hostClassifier) {
		this.onboardingService = onboardingService;
		this.mapper = mapper;
		this.hostClassifier = hostClassifier;
	}

	@PostMapping("/register")
	ResponseEntity<ClinicRegistrationResponse> register(HttpServletRequest servletRequest,
			@Valid @RequestBody ClinicRegistrationRequest request) {
		if (hostClassifier.classify(servletRequest.getServerName()) != HostKind.APEX_OR_RESERVED) {
			throw new AccessDeniedException("Clinic registration is only available on the onboarding host");
		}
		UUID clinicId = onboardingService.register(request);
		return ResponseEntity.created(URI.create("/clinics/" + clinicId))
				.body(mapper.toResponse(clinicId));
	}

}
