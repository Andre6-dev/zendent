package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import com.zendent.iam.domain.PasswordResetToken;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
