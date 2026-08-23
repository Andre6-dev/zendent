package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.RefreshToken;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/**
	 * Scoped to the active Clinic by {@code @TenantId} and by row-level
	 * security, so a token issued elsewhere is invisible rather than rejected.
	 */
	Optional<RefreshToken> findByTokenHash(String tokenHash);

}
