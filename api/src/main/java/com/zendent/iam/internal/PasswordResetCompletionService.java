package com.zendent.iam.internal;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.PasswordResetToken;
import com.zendent.shared.domain.BadRequestException;
import com.zendent.shared.domain.ErrorMessages;

/** Redeems a reset credential inside the Clinic already resolved by the host. */
@Service
public class PasswordResetCompletionService {

	private final PasswordResetTokenRepository tokenRepository;
	private final SingleUseSecretPolicy secretPolicy;
	private final PasswordEncoder passwordEncoder;
	private final Duration timeToLive;

	PasswordResetCompletionService(PasswordResetTokenRepository tokenRepository,
			SingleUseSecretPolicy secretPolicy, PasswordEncoder passwordEncoder,
			@Value("${zendent.password-reset.ttl}") Duration timeToLive) {
		this.tokenRepository = tokenRepository;
		this.secretPolicy = secretPolicy;
		this.passwordEncoder = passwordEncoder;
		this.timeToLive = timeToLive;
	}

	@Transactional
	public void complete(String token, String newPassword) {
		Instant now = Instant.now();
		PasswordResetToken resetToken = tokenRepository.findByTokenHash(secretPolicy.hash(token))
			.filter(candidate -> candidate.isRedeemableAt(now, timeToLive))
			.orElseThrow(() -> new BadRequestException(ErrorMessages.PASSWORD_RESET_NOT_REDEEMABLE));
		resetToken.user().changePassword(passwordEncoder.encode(newPassword), now);
		resetToken.markUsed(now);
	}
}
