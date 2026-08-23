package com.zendent.shared.domain;

/**
 * Thrown when a request is well-formed but collides with what already exists.
 * Mapped to HTTP 409 by {@link com.zendent.shared.web.GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
