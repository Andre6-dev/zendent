package com.zendent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 2.3.7: every Clinic is served from its own origin, so the policy is a
 * wildcard over the base domain rather than a list nobody can maintain.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aClinicSubdomainMayCallItsOwnApiWithCredentials() throws Exception {
		mockMvc.perform(options("/auth/login")
				.header("Origin", "http://acme.localhost:3000")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Content-Type"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://acme.localhost:3000"))
			.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void theOnboardingOriginMayCallTheApi() throws Exception {
		mockMvc.perform(options("/auth/register")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
	}

	@Test
	void theBearerHeaderIsAllowedOrEveryAuthenticatedCallWouldFail() throws Exception {
		mockMvc.perform(options("/members")
				.header("Origin", "http://acme.localhost:3000")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "Authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Headers", "Authorization"));
	}

	@Test
	void anOriginOutsideTheBaseDomainIsRefused() throws Exception {
		mockMvc.perform(options("/auth/login")
				.header("Origin", "https://zendent.app.attacker.example")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden());
	}

	@Test
	void preflightNeedsNoSessionEvenForAProtectedEndpoint() throws Exception {
		// The browser sends the preflight before it can attach a token, so a
		// policy that required one would make every protected call unreachable.
		mockMvc.perform(options("/members")
				.header("Origin", "http://acme.localhost:3000")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/auth/logout").header("Origin", "http://acme.localhost:3000"))
			.andExpect(status().isUnauthorized());
	}

}
