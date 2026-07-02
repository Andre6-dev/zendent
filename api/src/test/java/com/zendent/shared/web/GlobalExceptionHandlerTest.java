package com.zendent.shared.web;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zendent.shared.domain.NotFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link GlobalExceptionHandler} maps each exception in design D7's
 * table to the correct RFC 7807 {@code ProblemDetail} status and shape
 * (backend-platform spec.md "Global Error Handling").
 */
class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.setValidator(new LocalValidatorFactoryBean())
		.build();

	@Test
	void beanValidationFailureReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/probe/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void constraintViolationReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/probe/constraint-violation"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors.field").value("must not be blank"));
	}

	@Test
	void badCredentialsReturns401WithGenericDetail() throws Exception {
		mockMvc.perform(post("/probe/bad-credentials"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.detail").value("Invalid credentials"));
	}

	@Test
	void accessDeniedReturns403() throws Exception {
		mockMvc.perform(post("/probe/access-denied"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.detail").value("Access denied"));
	}

	@Test
	void notFoundReturns404WithDomainMessage() throws Exception {
		mockMvc.perform(post("/probe/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("Clinic 123 not found"));
	}

	@Test
	void dataIntegrityViolationReturns409() throws Exception {
		mockMvc.perform(post("/probe/conflict"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void unhandledExceptionReturns500WithOpaqueDetailAndNoStackTrace() throws Exception {
		mockMvc.perform(post("/probe/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.status").value(500))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
			.andExpect(content().string(not(containsString("something internal broke"))))
			.andExpect(content().string(not(containsString("RuntimeException"))));
	}

	@RestController
	@RequestMapping("/probe")
	static class ProbeController {

		@PostMapping("/validation")
		String validation(@Valid @RequestBody ValidationRequest request) {
			return "ok";
		}

		@PostMapping("/constraint-violation")
		String constraintViolation() {
			throw new ConstraintViolationException("invalid", Set.of(mockFieldViolation()));
		}

		@PostMapping("/bad-credentials")
		String badCredentials() {
			throw new BadCredentialsException("bad credentials");
		}

		@PostMapping("/access-denied")
		String accessDenied() {
			throw new AccessDeniedException("denied");
		}

		@PostMapping("/not-found")
		String notFound() {
			throw new NotFoundException("Clinic 123 not found");
		}

		@PostMapping("/conflict")
		String conflict() {
			throw new DataIntegrityViolationException("duplicate key");
		}

		@PostMapping("/boom")
		String boom() {
			throw new RuntimeException("something internal broke");
		}

		private static ConstraintViolation<?> mockFieldViolation() {
			@SuppressWarnings("unchecked")
			ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
			Path path = mock(Path.class);
			when(path.toString()).thenReturn("field");
			when(violation.getPropertyPath()).thenReturn(path);
			when(violation.getMessage()).thenReturn("must not be blank");
			return violation;
		}

	}

	record ValidationRequest(@NotBlank String name) {
	}

}
