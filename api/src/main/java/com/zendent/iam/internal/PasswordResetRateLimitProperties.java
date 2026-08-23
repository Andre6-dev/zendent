package com.zendent.iam.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("zendent.password-reset.rate-limit")
record PasswordResetRateLimitProperties(int maxRequests, Duration window) {

	PasswordResetRateLimitProperties {
		Assert.isTrue(maxRequests > 0, "Password reset rate-limit max requests must be positive");
		Assert.notNull(window, "Password reset rate-limit window is required");
		Assert.isTrue(!window.isNegative() && !window.isZero(), "Password reset rate-limit window must be positive");
	}

}
