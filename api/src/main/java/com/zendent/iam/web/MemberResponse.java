package com.zendent.iam.web;

import java.time.Instant;
import java.util.UUID;

/**
 * A Membership as the Clinic's own members see it. Carries no password hash and
 * no Clinic identifier: the caller's Clinic is the only one they can ever read,
 * so echoing it back tells them nothing they did not already supply.
 */
public record MemberResponse(
		UUID id,
		UUID userId,
		String email,
		String fullName,
		String role,
		String status,
		Instant memberSince) {
}
