package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.zendent.shared.tenancy.TenantContext;

/**
 * Issue #24: refresh tokens rotate, replay revokes the lineage, and a token is
 * resolvable only inside the Clinic that issued it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshAndLogoutIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

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
	void redeemingARefreshTokenRotatesItAndInvalidatesTheOnePresented() throws Exception {
		Session session = signIn();

		String rotated = mockMvc.perform(refresh(session.host(), session.refreshToken()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<String>read(rotated, "$.accessToken")).isNotBlank();
		assertThat(JsonPath.<String>read(rotated, "$.refreshToken"))
			.isNotBlank()
			.isNotEqualTo(session.refreshToken());

		// The presented token is spent: it no longer buys anything.
		mockMvc.perform(refresh(session.host(), session.refreshToken()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void theRotatedTokenItselfKeepsWorking() throws Exception {
		Session session = signIn();

		String rotated = mockMvc.perform(refresh(session.host(), session.refreshToken()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String next = JsonPath.read(rotated, "$.refreshToken");

		mockMvc.perform(refresh(session.host(), next)).andExpect(status().isOk());
	}

	@Test
	void replayingASpentTokenRevokesTheWholeLineage() throws Exception {
		Session session = signIn();

		String second = JsonPath.read(mockMvc.perform(refresh(session.host(), session.refreshToken()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.refreshToken");
		String third = JsonPath.read(mockMvc.perform(refresh(session.host(), second))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.refreshToken");

		// Replay the first, long since rotated away.
		mockMvc.perform(refresh(session.host(), session.refreshToken()))
			.andExpect(status().isUnauthorized());

		// The live descendant dies with it: every session in this lineage ends.
		mockMvc.perform(refresh(session.host(), third))
			.andExpect(status().isUnauthorized());
		assertThat(liveTokensFor(session.email())).isZero();
	}

	@Test
	void anUnknownOrMalformedTokenIsRefused() throws Exception {
		Session session = signIn();

		mockMvc.perform(refresh(session.host(), "not-a-real-token")).andExpect(status().isUnauthorized());
		mockMvc.perform(refresh(session.host(), UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
	}

	@Test
	void anExpiredTokenIsRefused() throws Exception {
		Session session = signIn();
		ownerJdbcTemplate.update("UPDATE refresh_token SET expires_at = now() - interval '1 day'");

		mockMvc.perform(refresh(session.host(), session.refreshToken())).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenIssuedByOneClinicCannotBeRedeemedUnderAnother() throws Exception {
		Session clinicA = signIn();
		Session clinicB = signIn();

		// The row genuinely exists — the table owner, exempt from no policy, sees
		// it. Only the tenant scope keeps it out of Clinic A's reach.
		Integer stored = ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM refresh_token t JOIN app_user u ON u.id = t.user_id WHERE u.email = ?",
				Integer.class, clinicB.email());
		assertThat(stored).isEqualTo(1);

		mockMvc.perform(refresh(clinicA.host(), clinicB.refreshToken()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesThePresentedTokenAndRequiresASession() throws Exception {
		Session session = signIn();

		mockMvc.perform(post("/auth/logout").with(serverName(session.host()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(session.refreshToken())))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/auth/logout").with(serverName(session.host()))
				.header("Authorization", session.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(session.refreshToken())))
			.andExpect(status().isNoContent());

		mockMvc.perform(refresh(session.host(), session.refreshToken()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aRefreshedSessionCanStillReachAProtectedEndpoint() throws Exception {
		Session session = signIn();

		String rotated = mockMvc.perform(refresh(session.host(), session.refreshToken()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/me").with(serverName(session.host()))
				.header("Authorization", "Bearer " + JsonPath.<String>read(rotated, "$.accessToken")))
			.andExpect(status().isOk());
	}

	@Test
	void theEndpointsDocumentTheirTagAndEveryStatusTheyCanReturn() throws Exception {
		String apiDocs = mockMvc.perform(get("/v3/api-docs").with(serverName("localhost")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(apiDocs, "$.paths./auth/refresh.post.tags"))
			.containsExactly("Authentication");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs, "$.paths./auth/refresh.post.responses").keySet())
			.containsExactlyInAnyOrder("200", "400", "401", "404");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs, "$.paths./auth/logout.post.responses").keySet())
			.containsExactlyInAnyOrder("204", "400", "401", "403", "404");
	}

	private Integer liveTokensFor(String email) {
		return ownerJdbcTemplate.queryForObject("""
				SELECT count(*) FROM refresh_token t JOIN app_user u ON u.id = t.user_id
				WHERE u.email = ? AND t.revoked_at IS NULL
				""", Integer.class, email);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(
			String host, String refreshToken) {
		return post("/auth/refresh").with(serverName(host))
			.contentType(MediaType.APPLICATION_JSON)
			.content(refreshBody(refreshToken));
	}

	private static String refreshBody(String refreshToken) {
		return """
				{"refreshToken":"%s"}
				""".formatted(refreshToken);
	}

	private Session signIn() throws Exception {
		UUID suffix = UUID.randomUUID();
		String slug = "clinic-" + suffix;
		String email = "admin-" + suffix + "@example.com";

		mockMvc.perform(post("/auth/register").with(serverName("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "clinicName": "Clinic %s",
						  "slug": "%s",
						  "adminEmail": "%s",
						  "adminPassword": "%s",
						  "adminFullName": "Ada Admin"
						}
						""".formatted(suffix, slug, email, PASSWORD)))
			.andExpect(status().isCreated());

		String login = mockMvc.perform(post("/auth/login").with(serverName(slug + ".localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		return new Session(slug, email, JsonPath.read(login, "$.accessToken"),
				JsonPath.read(login, "$.refreshToken"));
	}

	private static RequestPostProcessor serverName(String host) {
		return request -> {
			request.setServerName(host);
			return request;
		};
	}

	private record Session(String slug, String email, String accessToken, String refreshToken) {

		String host() {
			return slug + ".localhost";
		}

		String bearer() {
			return "Bearer " + accessToken;
		}

	}

}
