package com.zendent.iam.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Owns the policy for opaque, single-use secrets stored by the IAM module.
 *
 * <p>The plaintext value leaves this collaborator only so it can be returned to
 * its intended recipient. Persistence receives the derived hash instead.
 */
@Component
class SingleUseSecretPolicy {

	private static final int SECRET_BYTES = 32;
	private static final String PASSWORD_RESET_DOMAIN = "zendent:password-reset:";

	private final SecureRandom random;
	private final KeyedDerivationPolicy keyedDerivationPolicy;

	SingleUseSecretPolicy(SecureRandom random, KeyedDerivationPolicy keyedDerivationPolicy) {
		this.random = random;
		this.keyedDerivationPolicy = keyedDerivationPolicy;
	}

	MintedSecret mint() {
		byte[] bytes = new byte[SECRET_BYTES];
		random.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new MintedSecret(value, hash(value));
	}

	/**
	 * Derives a stable secret for a durable instruction without putting the
	 * plaintext in that instruction. The domain prefix prevents the JWT key from
	 * being used as though the two token formats were interchangeable.
	 */
	MintedSecret deriveForPasswordReset(UUID resetTokenId) {
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(
				keyedDerivationPolicy.derive(PASSWORD_RESET_DOMAIN, resetTokenId.toString()));
		return new MintedSecret(value, hash(value));
	}

	String hash(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every JVM", ex);
		}
	}

	record MintedSecret(String value, String hash) {
	}

}
