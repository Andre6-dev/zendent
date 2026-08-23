package com.zendent.shared.domain;

/** A well-formed request whose domain input cannot be accepted. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
