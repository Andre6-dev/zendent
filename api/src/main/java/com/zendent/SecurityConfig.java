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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.zendent.shared.web.ProblemDetailWriter;

/**
 * Application-wide security configuration (design D3, D7). Wires a stateless
 * HS256 JWT resource server from a single {@link SecretKeySpec} shared by the
 * {@link JwtEncoder} and {@link JwtDecoder} beans, and registers RFC 7807
 * {@code ProblemDetail} responses for auth failures raised inside the filter
 * chain (before Spring MVC dispatch — see design D7 "filter-chain gap").
 *
 * <p>This is infra plumbing ONLY: no login/token-issuance logic (PKG-2.2) and
 * no tenancy filters (wired here in PKG-2.3.5) yet. No protected business
 * endpoint exists yet in this PR, so every route is temporarily permitted.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] DOC_ENDPOINTS = {
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**",
	};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler)
			throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(DOC_ENDPOINTS).permitAll()
				// TODO(PKG-2.2/2.3): replace with real authorization rules once
				// protected business endpoints exist; none are built yet.
				.anyRequest().permitAll())
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
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
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
				ProblemDetailWriter.write(response, PROBLEM_DETAIL_MAPPER, HttpStatus.UNAUTHORIZED, "Invalid credentials");
	}

	@Bean
	AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) ->
				ProblemDetailWriter.write(response, PROBLEM_DETAIL_MAPPER, HttpStatus.FORBIDDEN, "Access denied");
	}

}
