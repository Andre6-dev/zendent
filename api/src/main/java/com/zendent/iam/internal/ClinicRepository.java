package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.Clinic;

interface ClinicRepository extends JpaRepository<Clinic, UUID> {

	Optional<Clinic> findBySlug(String slug);

}
