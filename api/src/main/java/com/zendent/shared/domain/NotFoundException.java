package com.zendent.shared.domain;

/**
 * Thrown when a requested domain resource does not exist. Mapped to HTTP 404
 * by {@link com.zendent.shared.web.GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
