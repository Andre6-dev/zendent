package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.zendent.iam.domain.Clinic;
import com.zendent.shared.tenancy.ClinicDirectory;

/**
 * Supplies {@code shared.tenancy} with slug resolution without exposing the
 * Clinic entity or its repository outside this module.
 */
@Component
class JpaClinicDirectory implements ClinicDirectory {

	private final ClinicRepository clinicRepository;

	JpaClinicDirectory(ClinicRepository clinicRepository) {
		this.clinicRepository = clinicRepository;
	}

	@Override
	public Optional<UUID> findIdBySlug(String slug) {
		return clinicRepository.findBySlug(slug).map(Clinic::id);
	}

}
