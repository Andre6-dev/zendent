package com.zendent.iam.mapper;

import java.util.Locale;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import com.zendent.iam.domain.Clinic;
import com.zendent.iam.domain.User;
import com.zendent.iam.web.ClinicRegistrationRequest;
import com.zendent.iam.web.ClinicRegistrationResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClinicOnboardingMapper {

	@Mapping(target = "name", source = "clinicName", qualifiedByName = "trim")
	@Mapping(target = "slug", source = "slug", qualifiedByName = "normalizeIdentifier")
	Clinic toClinic(ClinicRegistrationRequest request);

	@Mapping(target = "email", source = "request.adminEmail", qualifiedByName = "normalizeIdentifier")
	@Mapping(target = "passwordHash", source = "passwordHash")
	@Mapping(target = "fullName", source = "request.adminFullName", qualifiedByName = "trim")
	User toUser(ClinicRegistrationRequest request, String passwordHash);

	@Mapping(target = "clinicId", source = "clinicId")
	ClinicRegistrationResponse toResponse(UUID clinicId);

	@Named("trim")
	default String trim(String value) {
		return value.trim();
	}

	@Named("normalizeIdentifier")
	default String normalizeIdentifier(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

}
