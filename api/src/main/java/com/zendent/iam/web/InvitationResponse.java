package com.zendent.iam.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned once, to the administrator who issued it. The {@code token} is the
 * only plaintext copy that ever exists — the database keeps a hash — so it is
 * theirs to deliver.
 */
public record InvitationResponse(UUID id, String email, String role, Instant expiresAt, String token) {
}
