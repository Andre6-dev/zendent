package com.zendent.iam.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports who the caller is. Reads the signed token rather than the database:
 * the claims are exactly what the rest of the request is authorized against, so
 * this shows the caller the scope they actually have.
 */
@RestController
@Tag(name = "Session", description = "The caller's own session")
public class MeController {

	@GetMapping("/me")
	@Operation(summary = "Report the authenticated caller",
			description = "Returns the user, the Clinic, and the roles the current session carries.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The caller's session"),
			@ApiResponse(responseCode = "401", description = "Missing, malformed, expired, or untrusted token", content = @Content),
			@ApiResponse(responseCode = "403", description = "The session belongs to a different Clinic than this subdomain", content = @Content),
			@ApiResponse(responseCode = "404", description = "The host names no Clinic", content = @Content),
	})
	MeResponse me(@AuthenticationPrincipal Jwt token) {
		return new MeResponse(
				UUID.fromString(token.getSubject()),
				token.getClaimAsString("email"),
				UUID.fromString(token.getClaimAsString("clinic_id")),
				List.copyOf(token.getClaimAsStringList("roles")));
	}

}
