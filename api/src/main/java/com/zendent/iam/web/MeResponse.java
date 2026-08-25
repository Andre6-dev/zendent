package com.zendent.iam.web;

import java.util.List;
import java.util.UUID;

/**
 * The caller's own session.
 *
 * <p>Two kinds of thing travel together here. The identifiers, the email and the
 * roles are what the request is authorized against, read off the signed token.
 * The two names are for a person to read — a navigation bar has nothing to show
 * otherwise but a UUID — and they come from the database, because a display name
 * is not a claim and putting it in the token would mean a renamed Clinic kept its
 * old name until every session had expired.
 */
public record MeResponse(
		UUID userId,
		String email,
		UUID clinicId,
		String memberName,
		String clinicName,
		List<String> roles) {
}
