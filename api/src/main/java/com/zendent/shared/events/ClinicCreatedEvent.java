package com.zendent.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by {@code iam} when a new clinic finishes onboarding (design D6).
 * Durably recorded by the Spring Modulith event-publication registry; no
 * consumer exists yet in PKG-2.1 — this is a contract stub only, owned by
 * {@code shared} so {@code iam} never couples to a future consumer's
 * internals.
 *
 * @param clinicId  the id of the newly created clinic
 * @param slug      the clinic's globally-unique subdomain slug
 * @param occurredAt when the clinic was created
 */
public record ClinicCreatedEvent(UUID clinicId, String slug, Instant occurredAt) {
}
