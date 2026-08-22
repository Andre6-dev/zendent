package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * Issue #23: the first read surface over tenant-owned data, and the first place
 * isolation is observable over HTTP rather than at the database seam.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClinicMemberListingIntegrationTest {

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
	void aMemberListsTheirOwnClinicsMembershipsWithTheirRoles() throws Exception {
		Session session = signIn();

		mockMvc.perform(get("/members").with(serverName(session.host())).header("Authorization", session.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].email").value(session.email()))
			.andExpect(jsonPath("$[0].role").value("ADMIN"))
			.andExpect(jsonPath("$[0].fullName").value("Ada Admin"));
	}

	@Test
	void theListingNeverContainsAnotherClinicsMemberships() throws Exception {
		Session clinicA = signIn();
		Session clinicB = signIn();

		// Both Memberships exist — the table owner, exempt from no policy, sees
		// them. Only the tenant scope keeps B's out of A's listing.
		Integer total = ownerJdbcTemplate.queryForObject("SELECT count(*) FROM membership", Integer.class);
		assertThat(total).isGreaterThanOrEqualTo(2);

		String body = mockMvc.perform(get("/members").with(serverName(clinicA.host()))
				.header("Authorization", clinicA.bearer()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(body, "$[*].email"))
			.containsExactly(clinicA.email())
			.doesNotContain(clinicB.email());
	}

	@Test
	void aMembershipInTheCallersClinicIsReadableByItsIdentifier() throws Exception {
		Session session = signIn();
		UUID membershipId = onlyMembershipOf(session);

		mockMvc.perform(get("/members/" + membershipId).with(serverName(session.host()))
				.header("Authorization", session.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(membershipId.toString()))
			.andExpect(jsonPath("$.email").value(session.email()));
	}

	@Test
	void anotherClinicsMembershipIsIndistinguishableFromOneThatExistsNowhere() throws Exception {
		Session clinicA = signIn();
		Session clinicB = signIn();
		UUID membershipInB = onlyMembershipOf(clinicB);

		String foreign = mockMvc.perform(get("/members/" + membershipInB).with(serverName(clinicA.host()))
				.header("Authorization", clinicA.bearer()))
			.andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();
		String nowhere = mockMvc.perform(get("/members/" + UUID.randomUUID()).with(serverName(clinicA.host()))
				.header("Authorization", clinicA.bearer()))
			.andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();

		// Everything the server chose is identical. `instance` echoes the URI the
		// caller asked for, so it differs by construction and reveals nothing
		// they did not already know.
		for (String serverChosen : new String[] { "$.status", "$.title", "$.detail" }) {
			assertThat(JsonPath.<Object>read(foreign, serverChosen))
				.as(serverChosen)
				.isEqualTo(JsonPath.<Object>read(nowhere, serverChosen));
		}
		assertThat(JsonPath.<java.util.Map<String, ?>>read(foreign, "$").keySet())
			.isEqualTo(JsonPath.<java.util.Map<String, ?>>read(nowhere, "$").keySet());
	}

	@Test
	void theListingRequiresASession() throws Exception {
		Session session = signIn();

		mockMvc.perform(get("/members").with(serverName(session.host())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void theEndpointsDocumentTheirTagAndEveryStatusTheyCanReturn() throws Exception {
		String apiDocs = mockMvc.perform(get("/v3/api-docs").with(serverName("localhost")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(apiDocs, "$.paths./members.get.tags")).containsExactly("Members");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs, "$.paths./members.get.responses").keySet())
			.containsExactlyInAnyOrder("200", "401", "403", "404");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs,
				"$.paths./members/{memberId}.get.responses").keySet())
			.containsExactlyInAnyOrder("200", "401", "403", "404");
	}

	private UUID onlyMembershipOf(Session session) {
		return ownerJdbcTemplate.queryForObject(
				"SELECT m.id FROM membership m JOIN app_user u ON u.id = m.user_id WHERE u.email = ?",
				UUID.class, session.email());
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

		return new Session(slug, email, JsonPath.read(login, "$.accessToken"));
	}

	private static RequestPostProcessor serverName(String host) {
		return request -> {
			request.setServerName(host);
			return request;
		};
	}

	private record Session(String slug, String email, String accessToken) {

		String host() {
			return slug + ".localhost";
		}

		String bearer() {
			return "Bearer " + accessToken;
		}

	}

}
