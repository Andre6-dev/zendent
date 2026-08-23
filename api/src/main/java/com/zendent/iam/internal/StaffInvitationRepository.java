package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.StaffInvitation;

interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {

	/**
	 * Scoped to the active Clinic by {@code @TenantId} and by row-level
	 * security, so an invitation issued elsewhere is invisible rather than
	 * rejected.
	 */
	Optional<StaffInvitation> findByTokenHash(String tokenHash);

}
