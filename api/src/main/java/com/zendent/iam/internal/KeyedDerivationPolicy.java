package com.zendent.iam.internal;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

/** Derives domain-separated values from IAM's application secret. */
@Component
class KeyedDerivationPolicy {

	private final SecretKey derivationKey;

	KeyedDerivationPolicy(SecretKey derivationKey) {
		this.derivationKey = derivationKey;
	}

	byte[] derive(String domain, String value) {
		try {
			Mac hmac = Mac.getInstance("HmacSHA256");
			hmac.init(derivationKey);
			return hmac.doFinal((domain + value).getBytes(StandardCharsets.UTF_8));
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is required by every JVM", ex);
		}
	}

}
