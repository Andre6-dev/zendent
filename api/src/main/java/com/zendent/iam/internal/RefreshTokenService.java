package com.zendent.iam.internal;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.Membership;
import com.zendent.iam.domain.RefreshToken;
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

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;
	private final MembershipRepository membershipRepository;
	private final RefreshTokenLineage lineage;
	private final AccessTokenIssuer accessTokenIssuer;
	private final Duration timeToLive;

	RefreshTokenService(RefreshTokenRepository refreshTokenRepository, MembershipRepository membershipRepository,
			RefreshTokenLineage lineage, AccessTokenIssuer accessTokenIssuer,
			@Value("${zendent.jwt.refresh-token-ttl}") Duration timeToLive) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.membershipRepository = membershipRepository;
		this.lineage = lineage;
		this.accessTokenIssuer = accessTokenIssuer;
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
		RefreshToken current = refreshTokenRepository.findByTokenHash(hash(presented))
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

		current.revoke(now);
		return issue(membershipOf(current), current.id());
	}

	@Transactional
	public void revoke(String presented) {
		refreshTokenRepository.findByTokenHash(hash(presented))
			.ifPresent(token -> token.revoke(Instant.now()));
	}

	private LoginResponse issue(Membership membership, UUID rotatedFrom) {
		AccessTokenIssuer.AccessToken access = accessTokenIssuer.issue(membership);
		String refreshToken = newSecret();
		refreshTokenRepository.save(new RefreshToken(membership.clinicId(), membership.user().id(),
				hash(refreshToken), access.jti(), Instant.now().plus(timeToLive), rotatedFrom));
		return new LoginResponse(access.value(), "Bearer", access.expiresInSeconds(), refreshToken);
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

	private static String newSecret() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String hash(String token) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every JVM", ex);
		}
	}

}
