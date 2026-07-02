package com.zendent.shared.web;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Writes an RFC 7807 {@link ProblemDetail} body directly to the servlet
 * response. Security components that run inside the filter chain — before
 * Spring MVC dispatch, e.g. {@link org.springframework.security.web.AuthenticationEntryPoint}
 * and {@link org.springframework.security.web.access.AccessDeniedHandler} —
 * cannot rely on {@link GlobalExceptionHandler}'s {@code @RestControllerAdvice}
 * (see design D7 "filter-chain gap"), so they use this helper to produce the
 * same response shape.
 */
public final class ProblemDetailWriter {

	private ProblemDetailWriter() {
	}

	public static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String detail)
			throws IOException {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), problemDetail);
	}

}
