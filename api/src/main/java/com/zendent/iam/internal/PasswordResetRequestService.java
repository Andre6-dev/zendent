package com.zendent.iam.internal;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.Membership;
import com.zendent.iam.domain.PasswordResetToken;
import com.zendent.iam.domain.User;
import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.domain.TooManyRequestsException;

/** Issues reset credentials without revealing whether the submitted account exists. */
@Service
public class PasswordResetRequestService {

	private final UserRepository userRepository;
	private final MembershipRepository membershipRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final SingleUseSecretPolicy secretPolicy;
	private final PasswordResetRequestRateLimiter rateLimiter;
	private final ApplicationEventPublisher eventPublisher;

	PasswordResetRequestService(UserRepository userRepository, MembershipRepository membershipRepository,
			PasswordResetTokenRepository tokenRepository,
			SingleUseSecretPolicy secretPolicy, PasswordResetRequestRateLimiter rateLimiter,
			ApplicationEventPublisher eventPublisher) {
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.tokenRepository = tokenRepository;
		this.secretPolicy = secretPolicy;
		this.rateLimiter = rateLimiter;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public void request(String rawEmail, UUID clinicId) {
		String email = normalize(rawEmail);
		if (!rateLimiter.permit(clinicId, email)) {
			throw new TooManyRequestsException(ErrorMessages.PASSWORD_RESET_RATE_LIMITED);
		}
		Optional<User> user = userRepository.findByEmail(email);
		if (user.isEmpty() || membershipRepository.findByUserId(user.get().id())
				.filter(candidate -> candidate.status() == Membership.Status.ACTIVE)
				.isEmpty()) {
			return;
		}

		UUID resetTokenId = UUID.randomUUID();
		SingleUseSecretPolicy.MintedSecret token = secretPolicy.deriveForPasswordReset(resetTokenId);
		tokenRepository.save(new PasswordResetToken(resetTokenId, clinicId, user.get(), token.hash()));
		eventPublisher.publishEvent(new PasswordResetDeliveryRequested(
				resetTokenId, user.get().id(), clinicId));
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

}
