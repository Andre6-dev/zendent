package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.zendent.shared.tenancy.TenantContext;

/**
 * Issue #21: a session is issued on the Clinic's subdomain, and the Membership
 * lookup that decides the login is scoped by the database rather than by an
 * application condition.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClinicLoginIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtDecoder jwtDecoder;

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
	void loginOnTheClinicSubdomainReturnsAnAccessToken() throws Exception {
		Clinic clinic = registerClinic();

		mockMvc.perform(login(clinic.slug(), clinic.adminEmail(), PASSWORD))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@Test
	void theTokenNamesTheUserTheClinicAndTheRoles() throws Exception {
		Clinic clinic = registerClinic();

		String body = mockMvc.perform(login(clinic.slug(), clinic.adminEmail(), PASSWORD))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		Jwt token = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
		assertThat(token.getClaimAsString("clinic_id")).isEqualTo(clinic.id().toString());
		assertThat(token.getClaimAsString("email")).isEqualTo(clinic.adminEmail());
		assertThat(token.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(token.getSubject()).isNotBlank();
		assertThat(token.getId()).isNotBlank();
		assertThat(token.getExpiresAt()).isAfter(token.getIssuedAt());
	}

	@Test
	void aMembershipInAnotherClinicCannotAuthenticateHere() throws Exception {
		Clinic clinicA = registerClinic();
		Clinic clinicB = registerClinic();

		// The Membership genuinely exists — the table owner, who is exempt from
		// no policy, can see it. Only the tenant scope hides it from Clinic A.
		Integer memberships = ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM membership m JOIN app_user u ON u.id = m.user_id WHERE u.email = ?",
				Integer.class, clinicB.adminEmail());
		assertThat(memberships).isEqualTo(1);

		mockMvc.perform(login(clinicA.slug(), clinicB.adminEmail(), PASSWORD))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void anUnknownEmailAndAWrongPasswordFailIdentically() throws Exception {
		Clinic clinic = registerClinic();

		String unknownEmail = mockMvc.perform(login(clinic.slug(), "nobody@example.com", PASSWORD))
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		String wrongPassword = mockMvc.perform(login(clinic.slug(), clinic.adminEmail(), "not-the-password"))
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
	}

	@Test
	void loginOnTheApexOrAReservedHostFails() throws Exception {
		Clinic clinic = registerClinic();

		for (String host : new String[] { "localhost", "app.localhost", "www.localhost", "api.localhost" }) {
			mockMvc.perform(post("/auth/login").with(serverName(host))
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginBody(clinic.adminEmail(), PASSWORD)))
				.andExpect(status().isForbidden());
		}
	}

	@Test
	void nothingTheCallerSuppliesChangesWhichClinicTheLoginResolvesAgainst() throws Exception {
		Clinic clinicA = registerClinic();
		Clinic clinicB = registerClinic();

		String bodyNamingAnotherClinic = """
				{"email":"%s","password":"%s","clinicId":"%s","slug":"%s"}
				""".formatted(clinicB.adminEmail(), PASSWORD, clinicB.id(), clinicB.slug());

		mockMvc.perform(post("/auth/login").with(serverName(clinicA.slug() + ".localhost"))
				.queryParam("clinicId", clinicB.id().toString())
				.header("X-Clinic-Slug", clinicB.slug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(bodyNamingAnotherClinic))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void theLoginEndpointDocumentsItsTagAndEveryStatusItCanReturn() throws Exception {
		String apiDocs = mockMvc.perform(get("/v3/api-docs").with(serverName("localhost")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(apiDocs, "$.paths./auth/login.post.tags"))
			.containsExactly("Authentication");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs, "$.paths./auth/login.post.responses").keySet())
			.containsExactlyInAnyOrder("200", "400", "401", "403", "404");
		assertThat(JsonPath.<String>read(apiDocs, "$.paths./auth/login.post.summary")).isNotBlank();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
			String slug, String email, String password) {
		return post("/auth/login").with(serverName(slug + ".localhost"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(loginBody(email, password));
	}

	private static String loginBody(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}

	private Clinic registerClinic() throws Exception {
		UUID suffix = UUID.randomUUID();
		String slug = "clinic-" + suffix;
		String email = "admin-" + suffix + "@example.com";
		String body = """
				{
				  "clinicName": "Clinic %s",
				  "slug": "%s",
				  "adminEmail": "%s",
				  "adminPassword": "%s",
				  "adminFullName": "Ada Admin"
				}
				""".formatted(suffix, slug, email, PASSWORD);

		String response = mockMvc.perform(post("/auth/register").with(serverName("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return new Clinic(UUID.fromString(JsonPath.read(response, "$.clinicId")), slug, email);
	}

	private static RequestPostProcessor serverName(String host) {
		return request -> {
			request.setServerName(host);
			return request;
		};
	}

	private record Clinic(UUID id, String slug, String adminEmail) {
	}

}
