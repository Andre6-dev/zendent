package com.zendent.iam.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

import com.zendent.iam.domain.Membership;

/**
 * Builds the access token a session is carried by. The Clinic travels in the
 * token because it is signed: later requests read their Clinic from the claim
 * rather than re-deriving it from the host, which the caller controls.
 */
@Component
class AccessTokenIssuer {

	private final JwtEncoder jwtEncoder;
	private final String issuer;
	private final Duration timeToLive;

	AccessTokenIssuer(JwtEncoder jwtEncoder,
			@Value("${zendent.jwt.issuer}") String issuer,
			@Value("${zendent.jwt.access-token-ttl}") Duration timeToLive) {
		this.jwtEncoder = jwtEncoder;
		this.issuer = issuer;
		this.timeToLive = timeToLive;
	}

	AccessToken issue(Membership membership) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(timeToLive);
		String jti = UUID.randomUUID().toString();

		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(jti)
			.subject(membership.user().id().toString())
			.claim("clinic_id", membership.clinicId().toString())
			.claim("email", membership.user().email())
			.claim("roles", List.of(membership.role().code().name()))
			.build();

		String value = jwtEncoder
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new AccessToken(value, timeToLive.toSeconds(), jti);
	}

	record AccessToken(String value, long expiresInSeconds, String jti) {
	}

}
