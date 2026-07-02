package com.zendent.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link ProblemDetailWriter}: the servlet-response writer used
 * by filter-chain-stage security handlers (see design D7 "filter-chain gap").
 */
class ProblemDetailWriterTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void writesUnauthorizedProblemDetailToResponse() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		ProblemDetailWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "Invalid credentials");

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.getContentAsString())
			.contains("\"status\":401")
			.contains("\"detail\":\"Invalid credentials\"");
	}

	@Test
	void writesForbiddenProblemDetailToResponse() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		ProblemDetailWriter.write(response, objectMapper, HttpStatus.FORBIDDEN, "Access denied");

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.getContentAsString())
			.contains("\"status\":403")
			.contains("\"detail\":\"Access denied\"");
	}

}
