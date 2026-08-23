package com.zendent.shared.domain;

import java.util.UUID;

/**
 * An identifier that knows what it identifies.
 *
 * <p>Every identifier in this system is a {@link UUID}, which means the compiler
 * cannot tell a Clinic's from a User's — and a method taking two of them in a
 * row will accept them in the wrong order without complaint. Wrapping recovers
 * that check.
 *
 * <p>Deliberately not sealed: later modules add their own (a {@code PatientId},
 * an {@code AppointmentId}) from their own packages.
 */
public interface TypedId {

	UUID value();

}
