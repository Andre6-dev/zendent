package com.zendent;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.tenancy.AuthenticatedClinicFilter;
import com.zendent.shared.tenancy.SubdomainClinicResolutionFilter;
import com.zendent.shared.web.ProblemDetailWriter;

/**
 * Application-wide security configuration (design D3, D7). Wires a stateless
 * HS256 JWT resource server from a single {@link SecretKeySpec} shared by the
 * {@link JwtEncoder} and {@link JwtDecoder} beans, and registers RFC 7807
 * {@code ProblemDetail} responses for auth failures raised inside the filter
 * chain (before Spring MVC dispatch — see design D7 "filter-chain gap").
 *
 * <p>Two tenancy filters bracket authentication: the subdomain resolves a
 * Clinic before it (#20), and the signed claim overrides that resolution after
 * it (#22). Only onboarding, login and the API docs are public; everything else
 * requires a session.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
			"/auth/register",
			"/auth/login",
			// The access token it replaces may already have expired, so this
			// cannot require one.
			"/auth/refresh",
	};

	private static final String[] DOC_ENDPOINTS = {
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**",
	};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler,
			SubdomainClinicResolutionFilter subdomainClinicResolutionFilter,
			AuthenticatedClinicFilter authenticatedClinicFilter)
			throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			// The Clinic is resolved from the host before authentication, so a
			// public request (onboarding, login) is already scoped when it runs.
			.addFilterBefore(subdomainClinicResolutionFilter, BearerTokenAuthenticationFilter.class)
			// ...and the signed claim overrides it once authentication has run.
			.addFilterAfter(authenticatedClinicFilter, BearerTokenAuthenticationFilter.class)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(DOC_ENDPOINTS).permitAll()
				// Public by necessity: onboarding runs before a Clinic exists,
				// and login is how a session is obtained in the first place.
				.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	@Bean
	SecretKey jwtSecretKey(@Value("${zendent.jwt.secret}") String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, @Value("${zendent.jwt.issuer}") String issuer) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// A valid signature is not enough: the token must also come from this
		// issuer, so one minted elsewhere under a shared secret is refused.
		OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
		decoder.setJwtValidator(validator);
		return decoder;
	}

	// NOTE: intentionally NOT @Autowired from the application context. This
	// classpath carries both classic Jackson 2 (spring-boot-starter-jackson)
	// and Jackson 3 "tools.jackson" (pulled transitively by
	// spring-modulith-events-jackson); Spring Boot's JacksonAutoConfiguration
	// prefers the latter and registers a `JsonMapper` bean instead of a
	// classic `com.fasterxml.jackson.databind.ObjectMapper` bean, so there is
	// no app-wide ObjectMapper bean to inject. Both handlers below only ever
	// serialize a plain ProblemDetail with no extension properties, so a bare
	// ObjectMapper (no Spring Boot Jackson customization needed) is correct
	// and sidesteps the Jackson 2/3 co-existence ambiguity entirely.
	private static final ObjectMapper PROBLEM_DETAIL_MAPPER = new ObjectMapper();

	@Bean
	AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, authException) ->
				ProblemDetailWriter.write(response, PROBLEM_DETAIL_MAPPER, HttpStatus.UNAUTHORIZED, ErrorMessages.INVALID_CREDENTIALS);
	}

	@Bean
	AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) ->
				ProblemDetailWriter.write(response, PROBLEM_DETAIL_MAPPER, HttpStatus.FORBIDDEN, ErrorMessages.ACCESS_DENIED);
	}

}
