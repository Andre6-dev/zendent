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

import com.zendent.iam.internal.ClinicMemberService;

/**
 * Reports who the caller is.
 *
 * <p>The scope comes from the signed token: the claims are exactly what the rest
 * of the request is authorized against, so this shows the caller the scope they
 * actually have rather than a second opinion about it. The two display names
 * come from the database, because a name is something a Clinic can change and a
 * token issued before the change would go on asserting the old one.
 */
@RestController
@Tag(name = "Session", description = "The caller's own session")
public class MeController {

	private final ClinicMemberService memberService;

	MeController(ClinicMemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping("/me")
	@Operation(summary = "Report the authenticated caller",
			description = "Returns the user, the Clinic, and the roles the current session carries.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The caller's session"),
			@ApiResponse(responseCode = "401", description = "Missing, malformed, expired, or untrusted token", content = @Content),
			@ApiResponse(responseCode = "403", description = "The session belongs to a different Clinic than this subdomain", content = @Content),
			@ApiResponse(responseCode = "404", description = "The host names no Clinic, or the token names no Membership in it", content = @Content),
	})
	MeResponse me(@AuthenticationPrincipal Jwt token) {
		UUID userId = UUID.fromString(token.getSubject());
		SignedInMember member = memberService.describeSignedIn(userId);

		return new MeResponse(
				userId,
				token.getClaimAsString("email"),
				UUID.fromString(token.getClaimAsString("clinic_id")),
				member.memberName(),
				member.clinicName(),
				List.copyOf(token.getClaimAsStringList("roles")));
	}

}
