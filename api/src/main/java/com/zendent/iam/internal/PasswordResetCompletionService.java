package com.zendent.iam.internal;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.PasswordResetToken;
import com.zendent.iam.domain.User;
import com.zendent.shared.domain.BadRequestException;
import com.zendent.shared.domain.ErrorMessages;

/** Redeems a reset credential inside the Clinic already resolved by the host. */
@Service
public class PasswordResetCompletionService {

	private final PasswordResetTokenRepository tokenRepository;
	private final UserRepository userRepository;
	private final SingleUseSecretPolicy secretPolicy;
	private final PasswordEncoder passwordEncoder;
	private final Duration timeToLive;

	PasswordResetCompletionService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
			SingleUseSecretPolicy secretPolicy, PasswordEncoder passwordEncoder,
			@Value("${zendent.password-reset.ttl}") Duration timeToLive) {
		this.tokenRepository = tokenRepository;
		this.userRepository = userRepository;
		this.secretPolicy = secretPolicy;
		this.passwordEncoder = passwordEncoder;
		this.timeToLive = timeToLive;
	}

	@Transactional
	public void complete(String token, String newPassword) {
		Instant validationMoment = Instant.now();
		PasswordResetToken resetToken = tokenRepository.findByTokenHash(secretPolicy.hash(token))
			.filter(candidate -> candidate.isRedeemableAt(validationMoment, timeToLive))
			.orElseThrow(() -> new BadRequestException(ErrorMessages.PASSWORD_RESET_NOT_REDEEMABLE));
		String passwordHash = passwordEncoder.encode(newPassword);
		User user = userRepository.findByIdForCredentialChange(resetToken.user().id())
			.orElseThrow(() -> new IllegalStateException("Password reset token references no User"));
		// This timestamp must be captured after the User lock is acquired. A
		// concurrent refresh that won the lock has already committed its successor;
		// one that lost it will observe this epoch after the reset commits.
		Instant changedAt = Instant.now();
		user.changePassword(passwordHash, changedAt);
		resetToken.markUsed(changedAt);
	}
}
