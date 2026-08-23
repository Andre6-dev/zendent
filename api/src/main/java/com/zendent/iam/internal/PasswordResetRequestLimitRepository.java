package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.PasswordResetRequestLimit;

interface PasswordResetRequestLimitRepository extends JpaRepository<PasswordResetRequestLimit, UUID> {

	Optional<PasswordResetRequestLimit> findByEmailFingerprint(String emailFingerprint);

}
