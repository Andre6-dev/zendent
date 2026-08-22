package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.Membership;

interface MembershipRepository extends JpaRepository<Membership, UUID> {

	/**
	 * Scoped to the active Clinic by {@code @TenantId} and by row-level
	 * security, so a Membership in another Clinic is invisible rather than
	 * filtered out at the end.
	 */
	Optional<Membership> findByUserId(UUID userId);

}
