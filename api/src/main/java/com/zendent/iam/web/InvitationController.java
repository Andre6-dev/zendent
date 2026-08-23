package com.zendent.iam.web;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.zendent.iam.internal.StaffInvitationService;
import com.zendent.shared.tenancy.TenantContext;

@RestController
@RequestMapping("/invitations")
@Tag(name = "Invitations", description = "Offers of Membership in the caller's Clinic")
public class InvitationController {

	private final StaffInvitationService invitationService;

	InvitationController(StaffInvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Invite someone to the Clinic",
			description = "Administrators only: the ability to add members is the ability to grant access to "
					+ "health data. The response carries the only plaintext copy of the token.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Invitation issued"),
			@ApiResponse(responseCode = "400", description = "Invalid payload", content = @Content),
			@ApiResponse(responseCode = "401", description = "Missing, malformed, expired, or untrusted token", content = @Content),
			@ApiResponse(responseCode = "403", description = "Not an administrator of this Clinic", content = @Content),
			@ApiResponse(responseCode = "404", description = "The host names no Clinic", content = @Content),
			@ApiResponse(responseCode = "409", description = "That person already holds a Membership here", content = @Content),
	})
	ResponseEntity<InvitationResponse> invite(@Valid @RequestBody InvitationRequest request,
			@AuthenticationPrincipal Jwt caller) {
		InvitationResponse invitation = invitationService.invite(request,
				TenantContext.get().orElseThrow(),
				UUID.fromString(caller.getSubject()));
		return ResponseEntity.created(URI.create("/invitations/" + invitation.id())).body(invitation);
	}

	@PostMapping("/{token}/accept")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Redeem an invitation",
			description = "Public by necessity: the invited person has no session yet, so the token is the "
					+ "whole boundary. Unknown, expired, spent, and issued-by-another-Clinic all answer "
					+ "not-found, so a caller cannot probe for live tokens.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Membership created"),
			@ApiResponse(responseCode = "400", description = "Invalid payload", content = @Content),
			@ApiResponse(responseCode = "404", description = "No redeemable invitation for that token in this Clinic", content = @Content),
			@ApiResponse(responseCode = "409", description = "That person already holds a Membership here", content = @Content),
	})
	void accept(@PathVariable String token, @Valid @RequestBody InvitationAcceptance acceptance) {
		invitationService.accept(token, acceptance);
	}

}
