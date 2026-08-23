package com.zendent.iam.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.Membership;
import com.zendent.iam.domain.RefreshToken;
import com.zendent.iam.domain.User;
import com.zendent.iam.web.LoginResponse;
import com.zendent.shared.domain.ErrorMessages;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>Rotation means a redeemed token never works twice: each redemption spends
 * the one presented and returns a successor, so a captured token has a short
 * useful life and every redemption moves the target.
 *
 * <p>Replaying a spent token is the signal that something is wrong, and the
 * answer is to end the whole lineage rather than the one token — a legitimate
 * user signing in again is a far better outcome than an attacker keeping a
 * foothold.
 */
@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final MembershipRepository membershipRepository;
	private final RefreshTokenLineage lineage;
	private final AccessTokenIssuer accessTokenIssuer;
	private final SingleUseSecretPolicy secretPolicy;
	private final Duration timeToLive;

	RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
			MembershipRepository membershipRepository,
			RefreshTokenLineage lineage, AccessTokenIssuer accessTokenIssuer, SingleUseSecretPolicy secretPolicy,
			@Value("${zendent.jwt.refresh-token-ttl}") Duration timeToLive) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.lineage = lineage;
		this.accessTokenIssuer = accessTokenIssuer;
		this.secretPolicy = secretPolicy;
		this.timeToLive = timeToLive;
	}

	/** Issues the first token of a new lineage, alongside its access token. */
	LoginResponse startSession(Membership membership) {
		return issue(membership, null);
	}

	// Refusing a token is a business signal, not a failure of the work done
	// getting there: the lineage revocation below must survive it. Rolling back
	// would leave a compromised lineage alive, which is the one outcome this
	// method exists to prevent. A second transaction would be the other way to
	// hold it, but the test suite pins the pool to one connection on purpose —
	// to catch tenant leakage across reuse — so REQUIRES_NEW deadlocks.
	@Transactional(noRollbackFor = BadCredentialsException.class)
	public LoginResponse rotate(String presented) {
		Instant now = Instant.now();
		RefreshToken current = refreshTokenRepository.findByTokenHash(secretPolicy.hash(presented))
			.orElseThrow(() -> new BadCredentialsException(ErrorMessages.INVALID_REFRESH_TOKEN));

		if (current.isSpent()) {
			// Replay. Either this token was stolen or the chain is compromised,
			// and nothing here can tell the two apart.
			lineage.revokeAllRelatedTo(current.id(), now);
			throw new BadCredentialsException(ErrorMessages.INVALID_REFRESH_TOKEN);
		}
		if (current.hasExpiredBy(now)) {
			throw new BadCredentialsException(ErrorMessages.INVALID_REFRESH_TOKEN);
		}

		Membership membership = membershipOf(current);
		User user = userRepository.findByIdForCredentialCheck(current.userId())
			.orElseThrow(() -> new BadCredentialsException(ErrorMessages.INVALID_REFRESH_TOKEN));
		if (current.wasIssuedOnOrBefore(user.credentialsChangedAt())) {
			current.revoke(now);
			throw new BadCredentialsException(ErrorMessages.INVALID_REFRESH_TOKEN);
		}

		current.revoke(now);
		return issue(membership, current.id());
	}

	@Transactional
	public void revoke(String presented) {
		refreshTokenRepository.findByTokenHash(secretPolicy.hash(presented))
			.ifPresent(token -> token.revoke(Instant.now()));
	}

	private LoginResponse issue(Membership membership, UUID rotatedFrom) {
		AccessTokenIssuer.AccessToken access = accessTokenIssuer.issue(membership);
		SingleUseSecretPolicy.MintedSecret refreshToken = secretPolicy.mint();
		refreshTokenRepository.save(new RefreshToken(membership.clinicId(), membership.user().id(),
				refreshToken.hash(), access.jti(), Instant.now().plus(timeToLive), rotatedFrom));
		return new LoginResponse(access.value(), "Bearer", access.expiresInSeconds(), refreshToken.value());
	}

	/**
	 * Re-reads the Membership so a rotation cannot outlive it: a Membership
	 * revoked since the session started must not keep minting access tokens.
	 * Tenant-scoped, so this can only ever find one in the active Clinic.
	 */
	private Membership membershipOf(RefreshToken token) {
		return membershipRepository.findByUserId(token.userId())
			.filter(membership -> membership.status() == Membership.Status.ACTIVE)
			.orElseThrow(() -> new BadCredentialsException(ErrorMessages.INVALID_REFRESH_TOKEN));
	}

}
