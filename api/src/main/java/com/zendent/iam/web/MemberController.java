package com.zendent.iam.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zendent.iam.internal.ClinicMemberService;

/**
 * The Clinic's own staff directory. The Clinic is never in the path: it comes
 * from the session, so there is no identifier a caller could substitute.
 */
@RestController
@RequestMapping("/members")
@Tag(name = "Members", description = "The Memberships of the caller's Clinic")
public class MemberController {

	private final ClinicMemberService memberService;

	MemberController(ClinicMemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping
	@Operation(summary = "List the Clinic's members",
			description = "Every Membership in the caller's Clinic, with the role it grants.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The Clinic's Memberships"),
			@ApiResponse(responseCode = "401", description = "Missing, malformed, expired, or untrusted token", content = @Content),
			@ApiResponse(responseCode = "403", description = "The session belongs to a different Clinic than this subdomain", content = @Content),
			@ApiResponse(responseCode = "404", description = "The host names no Clinic", content = @Content),
	})
	List<MemberResponse> listMembers() {
		return memberService.listMembers();
	}

	@GetMapping("/{memberId}")
	@Operation(summary = "Read one member",
			description = "A Membership in the caller's Clinic. One belonging to another Clinic answers "
					+ "not-found, indistinguishably from an identifier that exists nowhere.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The Membership"),
			@ApiResponse(responseCode = "401", description = "Missing, malformed, expired, or untrusted token", content = @Content),
			@ApiResponse(responseCode = "403", description = "The session belongs to a different Clinic than this subdomain", content = @Content),
			@ApiResponse(responseCode = "404", description = "No such Membership in this Clinic", content = @Content),
	})
	MemberResponse findMember(@PathVariable UUID memberId) {
		return memberService.findMember(memberId);
	}

}
