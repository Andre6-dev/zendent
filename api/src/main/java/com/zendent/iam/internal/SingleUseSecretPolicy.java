package com.zendent.iam.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

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
	private final SecretKey derivationKey;

	SingleUseSecretPolicy(SecureRandom random, SecretKey derivationKey) {
		this.random = random;
		this.derivationKey = derivationKey;
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
		try {
			Mac hmac = Mac.getInstance("HmacSHA256");
			hmac.init(derivationKey);
			String value = Base64.getUrlEncoder().withoutPadding().encodeToString(
					hmac.doFinal((PASSWORD_RESET_DOMAIN + resetTokenId)
						.getBytes(StandardCharsets.UTF_8)));
			return new MintedSecret(value, hash(value));
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is required by every JVM", ex);
		}
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
