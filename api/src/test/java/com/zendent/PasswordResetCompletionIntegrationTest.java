package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.zendent.shared.tenancy.TenantContext;

/** Issue #41: redeem a password reset link and choose a new password. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetCompletionIntegrationTest {

	private static final String OLD_PASSWORD = "correct-horse-battery-staple";
	private static final String NEW_PASSWORD = "even-better-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PostgreSQLContainer postgresContainer;

	private JdbcTemplate ownerJdbcTemplate;

	@BeforeEach
	void connectAsTableOwner() {
		ownerJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
				postgresContainer.getJdbcUrl(),
				postgresContainer.getUsername(),
				postgresContainer.getPassword()));
		TenantContext.clear();
	}

	@Test
	void aValidTokenReplacesTheCredential() throws Exception {
		Clinic clinic = registerClinic();
		String token = "reset-" + UUID.randomUUID();
		insertResetToken(clinic, token);

		mockMvc.perform(resetPassword(clinic, token, NEW_PASSWORD))
			.andExpect(status().isNoContent());

		mockMvc.perform(login(clinic, NEW_PASSWORD)).andExpect(status().isOk());
		mockMvc.perform(login(clinic, OLD_PASSWORD)).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenCannotBeUsedTwiceAndTheCredentialDoesNotChangeAgain() throws Exception {
		Clinic clinic = registerClinic();
		String token = "reset-" + UUID.randomUUID();
		insertResetToken(clinic, token);

		mockMvc.perform(resetPassword(clinic, token, NEW_PASSWORD))
			.andExpect(status().isNoContent());
		mockMvc.perform(resetPassword(clinic, token, "third-password-that-must-not-win"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		mockMvc.perform(login(clinic, NEW_PASSWORD)).andExpect(status().isOk());
		mockMvc.perform(login(clinic, "third-password-that-must-not-win"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void anExpiredTokenIsRefusedWithoutChangingTheCredential() throws Exception {
		Clinic clinic = registerClinic();
		String token = "reset-" + UUID.randomUUID();
		insertResetToken(clinic, token);
		ownerJdbcTemplate.update("""
				UPDATE password_reset_token
				SET created_at = now() - interval '2 hours'
				WHERE token_hash = ?
				""", sha256(token));

		mockMvc.perform(resetPassword(clinic, token, NEW_PASSWORD))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		mockMvc.perform(login(clinic, OLD_PASSWORD)).andExpect(status().isOk());
		mockMvc.perform(login(clinic, NEW_PASSWORD)).andExpect(status().isUnauthorized());
	}

	@Test
	void anInvalidNewPasswordListsItsFailureAndLeavesTheTokenUsable() throws Exception {
		Clinic clinic = registerClinic();
		String token = "reset-" + UUID.randomUUID();
		insertResetToken(clinic, token);

		mockMvc.perform(resetPassword(clinic, token, "too-short"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Validation failed"))
			.andExpect(jsonPath("$.errors.newPassword").isNotEmpty());

		mockMvc.perform(login(clinic, OLD_PASSWORD)).andExpect(status().isOk());
		mockMvc.perform(resetPassword(clinic, token, NEW_PASSWORD))
			.andExpect(status().isNoContent());
		mockMvc.perform(login(clinic, NEW_PASSWORD)).andExpect(status().isOk());
	}

	@Test
	void aPasswordBeyondBcryptsUtf8LimitIsAValidationFailureAndLeavesTheTokenUsable() throws Exception {
		Clinic clinic = registerClinic();
		String token = "reset-" + UUID.randomUUID();
		insertResetToken(clinic, token);

		mockMvc.perform(resetPassword(clinic, token, "🔒".repeat(30)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Validation failed"))
			.andExpect(jsonPath("$.errors.newPassword").value("must be at most 72 UTF-8 bytes"));

		mockMvc.perform(resetPassword(clinic, token, NEW_PASSWORD))
			.andExpect(status().isNoContent());
		mockMvc.perform(login(clinic, NEW_PASSWORD)).andExpect(status().isOk());
	}

	@Test
	void resetPasswordEndpointDocumentsItsTagAndEveryStatusItCanReturn() throws Exception {
		String apiDocs = mockMvc.perform(get("/v3/api-docs").with(serverName("localhost")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<java.util.List<String>>read(
				apiDocs, "$.paths./auth/reset-password.post.tags"))
			.containsExactly("Authentication");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(
				apiDocs, "$.paths./auth/reset-password.post.responses").keySet())
			.containsExactlyInAnyOrder("204", "400", "404");
		assertThat(JsonPath.<String>read(
				apiDocs, "$.paths./auth/reset-password.post.summary")).isNotBlank();
	}

	private void insertResetToken(Clinic clinic, String token) throws Exception {
		ownerJdbcTemplate.update("""
				INSERT INTO password_reset_token (id, clinic_id, user_id, token_hash)
				VALUES (?, ?, ?, ?)
				""", UUID.randomUUID(), clinic.id(), clinic.adminId(), sha256(token));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resetPassword(
			Clinic clinic, String token, String newPassword) {
		return post("/auth/reset-password").with(serverName(clinic.host()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token":"%s","newPassword":"%s"}
					""".formatted(token, newPassword));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
			Clinic clinic, String password) {
		return post("/auth/login").with(serverName(clinic.host()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email":"%s","password":"%s"}
					""".formatted(clinic.adminEmail(), password));
	}

	private Clinic registerClinic() throws Exception {
		UUID suffix = UUID.randomUUID();
		String slug = "clinic-" + suffix;
		String email = "admin-" + suffix + "@example.com";
		String response = mockMvc.perform(post("/auth/register").with(serverName("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "clinicName": "Clinic %s",
						  "slug": "%s",
						  "adminEmail": "%s",
						  "adminPassword": "%s",
						  "adminFullName": "Ada Admin"
						}
						""".formatted(suffix, slug, email, OLD_PASSWORD)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		UUID clinicId = UUID.fromString(JsonPath.read(response, "$.clinicId"));
		UUID adminId = ownerJdbcTemplate.queryForObject(
				"SELECT id FROM app_user WHERE email = ?", UUID.class, email);
		return new Clinic(clinicId, slug, email, adminId);
	}

	private static String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
			.digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	private static RequestPostProcessor serverName(String host) {
		return request -> {
			request.setServerName(host);
			return request;
		};
	}

	private record Clinic(UUID id, String slug, String adminEmail, UUID adminId) {

		String host() {
			return slug + ".localhost";
		}
	}
}
