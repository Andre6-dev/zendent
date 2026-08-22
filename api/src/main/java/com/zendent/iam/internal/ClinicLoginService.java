package com.zendent.iam.internal;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.Membership;
import com.zendent.iam.domain.User;
import com.zendent.iam.web.LoginResponse;
import com.zendent.shared.domain.ErrorMessages;

/**
 * Authenticates a user against the Clinic the request already resolved to.
 *
 * <p>The Membership lookup is what decides the login, and it is scoped by the
 * database rather than by a condition written here: a Membership in another
 * Clinic is invisible, not merely filtered. An application-layer check could be
 * bypassed by a future code path that forgets it; the scope cannot.
 *
 * <p>Every failure — unknown email, wrong password, Membership elsewhere —
 * raises the same exception, so a caller learns nothing about which accounts
 * exist or where they hold Memberships.
 */
@Service
public class ClinicLoginService {

	private final UserRepository userRepository;
	private final MembershipRepository membershipRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccessTokenIssuer accessTokenIssuer;

	ClinicLoginService(UserRepository userRepository, MembershipRepository membershipRepository,
			PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokenIssuer) {
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.passwordEncoder = passwordEncoder;
		this.accessTokenIssuer = accessTokenIssuer;
	}

	@Transactional(readOnly = true)
	public LoginResponse authenticate(String email, String password) {
		Optional<User> user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));

		// The password is verified even when no user matched, so that an unknown
		// email and a known one cost the same time to reject.
		boolean passwordMatches = passwordEncoder.matches(password,
				user.map(User::passwordHash).orElse(NO_SUCH_USER_HASH));
		if (user.isEmpty() || !passwordMatches) {
			throw new BadCredentialsException(ErrorMessages.INVALID_CREDENTIALS);
		}

		Membership membership = membershipRepository.findByUserId(user.get().id())
			.filter(candidate -> candidate.status() == Membership.Status.ACTIVE)
			.orElseThrow(() -> new BadCredentialsException(ErrorMessages.INVALID_CREDENTIALS));

		AccessTokenIssuer.AccessToken token = accessTokenIssuer.issue(membership);
		return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds());
	}

	/**
	 * A structurally valid BCrypt hash of a value nobody holds, so the encoder
	 * does its full work on the no-such-user path instead of returning early.
	 */
	private static final String NO_SUCH_USER_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

}
