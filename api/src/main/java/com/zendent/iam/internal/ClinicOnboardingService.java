package com.zendent.iam.internal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.Clinic;
import com.zendent.iam.domain.Role;
import com.zendent.iam.domain.User;
import com.zendent.iam.mapper.ClinicOnboardingMapper;
import com.zendent.iam.web.ClinicRegistrationRequest;
import com.zendent.shared.events.ClinicCreatedEvent;

@Service
public class ClinicOnboardingService {

	private final ClinicRepository clinicRepository;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final JdbcTemplate jdbcTemplate;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;
	private final OnboardingClinicScope onboardingClinicScope;
	private final ClinicOnboardingMapper mapper;

	ClinicOnboardingService(ClinicRepository clinicRepository, UserRepository userRepository,
			RoleRepository roleRepository, JdbcTemplate jdbcTemplate,
			PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher,
			OnboardingClinicScope onboardingClinicScope, ClinicOnboardingMapper mapper) {
		this.clinicRepository = clinicRepository;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
		this.eventPublisher = eventPublisher;
		this.onboardingClinicScope = onboardingClinicScope;
		this.mapper = mapper;
	}

	@Transactional
	public UUID register(ClinicRegistrationRequest request) {
		Clinic clinic = clinicRepository.saveAndFlush(mapper.toClinic(request));
		User user = userRepository.saveAndFlush(
				mapper.toUser(request, passwordEncoder.encode(request.adminPassword())));
		Role adminRole = roleRepository.findByCode(Role.Code.ADMIN)
				.orElseThrow(() -> new IllegalStateException("ADMIN role is not configured"));

		onboardingClinicScope.fromNewClinic(clinic.id(), () -> jdbcTemplate.update("""
				INSERT INTO membership (id, clinic_id, user_id, role_id, status)
				VALUES (?, ?, ?, ?, 'ACTIVE')
				""", UUID.randomUUID(), clinic.id(), user.id(), adminRole.id()));
		eventPublisher.publishEvent(new ClinicCreatedEvent(clinic.id(), clinic.slug(), Instant.now()));

		return clinic.id();
	}

}
