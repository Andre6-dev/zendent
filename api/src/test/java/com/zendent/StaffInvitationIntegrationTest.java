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
 * Issue #25: only administrators invite, and an invitation is redeemable only
 * inside the Clinic that issued it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffInvitationIntegrationTest {

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
	void anAdministratorInvitesSomeoneToTheirClinic() throws Exception {
		Session admin = signIn();

		mockMvc.perform(invite(admin, "dentist@example.com", "DENTIST"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("dentist@example.com"))
			.andExpect(jsonPath("$.role").value("DENTIST"))
			.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void theDatabaseNeverHoldsAUsableInvitationToken() throws Exception {
		Session admin = signIn();
		String token = tokenOf(mockMvc.perform(invite(admin, "dentist@example.com", "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		Integer plaintextRows = ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM staff_invitation WHERE token_hash = ?", Integer.class, token);
		assertThat(plaintextRows).isZero();
	}

	@Test
	void acceptingCreatesTheMembershipAndTheUserIdentityWhenItIsNew() throws Exception {
		Session admin = signIn();
		String email = "dentist-" + UUID.randomUUID() + "@example.com";
		String token = tokenOf(mockMvc.perform(invite(admin, email, "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		mockMvc.perform(accept(admin.host(), token, "Dee Dentist"))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/members").with(serverName(admin.host())).header("Authorization", admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));

		// The invited person can now sign in and is a Dentist, not an administrator.
		String bearer = "Bearer " + JsonPath.<String>read(login(admin.slug(), email), "$.accessToken");
		mockMvc.perform(get("/me").with(serverName(admin.host())).header("Authorization", bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[0]").value("DENTIST"));
	}

	@Test
	void acceptingLinksAnIdentityThatAlreadyExistsElsewhere() throws Exception {
		Session clinicA = signIn();
		Session clinicB = signIn();

		String token = tokenOf(mockMvc.perform(invite(clinicB, clinicA.email(), "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mockMvc.perform(accept(clinicB.host(), token, "Ada Admin"))
			.andExpect(status().isCreated());

		// One person, one identity, two Memberships — the Dentist who works at
		// two practices.
		Integer identities = ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM app_user WHERE email = ?", Integer.class, clinicA.email());
		Integer memberships = ownerJdbcTemplate.queryForObject("""
				SELECT count(*) FROM membership m JOIN app_user u ON u.id = m.user_id WHERE u.email = ?
				""", Integer.class, clinicA.email());
		assertThat(identities).isEqualTo(1);
		assertThat(memberships).isEqualTo(2);
	}

	@Test
	void aMemberWithoutTheAdministratorRoleCannotInvite() throws Exception {
		Session admin = signIn();
		String email = "dentist-" + UUID.randomUUID() + "@example.com";
		String token = tokenOf(mockMvc.perform(invite(admin, email, "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mockMvc.perform(accept(admin.host(), token, "Dee Dentist")).andExpect(status().isCreated());

		Session dentist = new Session(admin.slug(), email,
				JsonPath.read(login(admin.slug(), email), "$.accessToken"));

		mockMvc.perform(invite(dentist, "someone-else@example.com", "STAFF"))
			.andExpect(status().isForbidden());
	}

	@Test
	void anInvitationCannotBeRedeemedTwice() throws Exception {
		Session admin = signIn();
		String email = "dentist-" + UUID.randomUUID() + "@example.com";
		String token = tokenOf(mockMvc.perform(invite(admin, email, "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		mockMvc.perform(accept(admin.host(), token, "Dee Dentist")).andExpect(status().isCreated());
		mockMvc.perform(accept(admin.host(), token, "Dee Dentist")).andExpect(status().isNotFound());
	}

	@Test
	void anExpiredInvitationIsRefused() throws Exception {
		Session admin = signIn();
		String email = "dentist-" + UUID.randomUUID() + "@example.com";
		String token = tokenOf(mockMvc.perform(invite(admin, email, "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		ownerJdbcTemplate.update(
				"UPDATE staff_invitation SET expires_at = now() - interval '1 day' WHERE email = ?", email);

		mockMvc.perform(accept(admin.host(), token, "Dee Dentist")).andExpect(status().isNotFound());
	}

	@Test
	void anInvitationFromOneClinicCannotBeRedeemedUnderAnother() throws Exception {
		Session clinicA = signIn();
		Session clinicB = signIn();
		String email = "dentist-" + UUID.randomUUID() + "@example.com";
		String token = tokenOf(mockMvc.perform(invite(clinicB, email, "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		// The invitation genuinely exists — the table owner, exempt from no
		// policy, sees it. Only the tenant scope keeps it out of A's reach.
		Integer stored = ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM staff_invitation WHERE email = ?", Integer.class, email);
		assertThat(stored).isEqualTo(1);

		mockMvc.perform(accept(clinicA.host(), token, "Dee Dentist"))
			.andExpect(status().isNotFound());
	}

	@Test
	void invitingSomeoneWhoAlreadyBelongsToTheClinicIsAConflict() throws Exception {
		Session admin = signIn();

		mockMvc.perform(invite(admin, admin.email(), "DENTIST"))
			.andExpect(status().isConflict());
	}

	@Test
	void redeemingTwoInvitationsForTheSamePersonIsAConflict() throws Exception {
		Session admin = signIn();
		String email = "dentist-" + UUID.randomUUID() + "@example.com";
		String first = tokenOf(mockMvc.perform(invite(admin, email, "DENTIST"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		String second = tokenOf(mockMvc.perform(invite(admin, email, "STAFF"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		mockMvc.perform(accept(admin.host(), first, "Dee Dentist")).andExpect(status().isCreated());
		mockMvc.perform(accept(admin.host(), second, "Dee Dentist")).andExpect(status().isConflict());
	}

	@Test
	void theEndpointsDocumentTheirTagAndEveryStatusTheyCanReturn() throws Exception {
		String apiDocs = mockMvc.perform(get("/v3/api-docs").with(serverName("localhost")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(apiDocs, "$.paths./invitations.post.tags"))
			.containsExactly("Invitations");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs, "$.paths./invitations.post.responses").keySet())
			.containsExactlyInAnyOrder("201", "400", "401", "403", "404", "409");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs,
				"$.paths./invitations/{token}/accept.post.responses").keySet())
			.containsExactlyInAnyOrder("201", "400", "404", "409");
	}

	private static String tokenOf(String invitationResponse) {
		return JsonPath.read(invitationResponse, "$.token");
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder invite(
			Session session, String email, String role) {
		return post("/invitations").with(serverName(session.host()))
			.header("Authorization", session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email":"%s","role":"%s"}
					""".formatted(email, role));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder accept(
			String host, String token, String fullName) {
		return post("/invitations/" + token + "/accept").with(serverName(host))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","password":"%s"}
					""".formatted(fullName, PASSWORD));
	}

	private String login(String slug, String email) throws Exception {
		return mockMvc.perform(post("/auth/login").with(serverName(slug + ".localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
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

		return new Session(slug, email, JsonPath.read(login(slug, email), "$.accessToken"));
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
