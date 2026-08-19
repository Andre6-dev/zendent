package com.zendent;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI bean exposing a JWT bearer security scheme, so Swagger UI
 * can authorize requests once protected endpoints exist (PKG-2.2/2.3).
 * Backend-platform spec.md "API Documentation".
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SECURITY_SCHEME = "bearerAuth";

	@Bean
	OpenAPI zendentOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("Zendent API")
				.description("Multi-tenant dental clinic management platform API")
				.version("v0"))
			.components(new Components()
				.addSecuritySchemes(BEARER_SECURITY_SCHEME, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SECURITY_SCHEME));
	}

}
