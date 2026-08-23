package com.zendent.iam.internal;

import java.util.UUID;

/**
 * Durable instruction containing no plaintext secret or email address. The
 * listener resolves global identity data and derives the reset secret from the
 * reset row identifier after the request commits.
 */
public record PasswordResetDeliveryRequested(UUID resetTokenId, UUID userId, UUID clinicId) {
}
