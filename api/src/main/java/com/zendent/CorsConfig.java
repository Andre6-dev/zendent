package com.zendent;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin policy for a product where every Clinic is its own origin.
 *
 * <p>The frontend calls the subdomain it is served from, so the allowed origins
 * are a wildcard over the base domain plus the apex that onboarding lives on.
 * Hence origin <em>patterns</em>: a literal {@code "*"} cannot be combined with
 * credentials, and Clinics are created at runtime so no list could be kept.
 *
 * <p>The patterns are configuration rather than being derived here, so
 * production cannot inherit a development origin by accident.
 */
@Configuration
@EnableConfigurationProperties(CorsConfig.CorsProperties.class)
public class CorsConfig {

	@ConfigurationProperties(prefix = "zendent.cors")
	record CorsProperties(List<String> allowedOriginPatterns) {
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(List.copyOf(properties.allowedOriginPatterns()));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// The browser must be allowed to send the bearer token to the Clinic's
		// own origin; without this every authenticated request fails CORS.
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(1800L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
