package com.zendent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the {@link SecurityConfig} filter-chain skeleton permits the
 * Springdoc/Swagger endpoints configured by {@link OpenApiConfig}, and that
 * the app boots with the JWT encoder/decoder beans wired (task 2.1.7,
 * 2.1.11; backend-platform spec.md "API Documentation").
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsAndSwaggerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsAreReachableWithoutAuthenticationAndExposeBearerScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("\"bearerAuth\"")));
	}

	@Test
	void swaggerUiIsReachableWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
			.andExpect(status().isOk());
	}

}
