package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;
import com.zendent.shared.tenancy.TenantContext;

/**
 * Issue #13: proves Clinic isolation at the HTTP seam.
 *
 * <p>Every other isolation test proves something narrower. {@code
 * RowLevelSecurityIntegrationTest} proves the policies exist, but it holds a raw
 * connection and sets the Clinic by hand — it never exercises a request. The
 * per-feature tests exercise requests, but every one of them would still pass
 * with row-level security switched off, because Hibernate's {@code @TenantId}
 * alone accounts for their results.
 *
 * <p>What is unproven between those two is whether the transaction hook fires
 * under real request conditions: the real filter chain, the real pool, a real
 * authenticated caller. So the probe below reads a tenant-owned table with
 * native SQL carrying no Clinic condition, from inside a request. Hibernate is
 * not in that path, so only the database can be filtering the rows.
 *
 * <p>The pool is pinned to one connection, which makes "the next request on the
 * same connection" the only thing that can happen rather than something the test
 * hopes for.
 */
@Import({ TestcontainersConfiguration.class, TenantIsolationOverHttpIntegrationTest.NativeReadProbe.class })
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIsolationOverHttpIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void clearClinic() {
		TenantContext.clear();
	}

	@Test
	void nativeSqlInsideARealRequestSeesOnlyTheCallersClinic() throws Exception {
		Clinic clinicA = register();
		Clinic clinicB = register();

		Probe probe = probe(clinicA);

		// No Clinic condition was written into that query. The rows are missing
		// because the database refused them, which is the whole point of the
		// second layer — @TenantId is not in this path at all.
		assertThat(probe.emails()).containsExactly(clinicA.email()).doesNotContain(clinicB.email());
	}

	@Test
	void theActiveClinicIsPublishedToTheDatabaseForEveryAuthenticatedRequest() throws Exception {
		Clinic clinic = register();

		assertThat(probe(clinic).clinicSetting()).isEqualTo(clinic.id().toString());
	}

	@Test
	void sequentialRequestsForDifferentClinicsOverOneConnectionEachSeeOnlyTheirOwn() throws Exception {
		Clinic clinicA = register();
		Clinic clinicB = register();

		// Interleaved on purpose: a Clinic that survived into the next request
		// would show up as A reading B's rows, or B reading A's.
		for (int round = 0; round < 3; round++) {
			assertThat(probe(clinicA).emails()).containsExactly(clinicA.email());
			assertThat(probe(clinicB).emails()).containsExactly(clinicB.email());
		}
	}

	@Test
	void theClinicDoesNotSurviveIntoAnUnauthenticatedRequestOnTheSameConnection() throws Exception {
		Clinic clinic = register();
		probe(clinic);

		// Onboarding refuses to run with a Clinic active. That it still succeeds
		// here is evidence the previous request left nothing behind.
		mockMvc.perform(post("/auth/register").with(serverName("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(UUID.randomUUID())))
			.andExpect(status().isCreated());
	}

	@Test
	void noneOfAnotherClinicsArtifactsWorkUnderThisSession() throws Exception {
		Clinic clinicA = register();
		Clinic clinicB = register();
		String invitationForB = invitationToken(clinicB);

		// Its Membership, by an identifier Clinic A genuinely knows.
		mockMvc.perform(get("/members/" + membershipId(clinicB)).with(serverName(clinicA.host()))
				.header("Authorization", clinicA.bearer()))
			.andExpect(status().isNotFound());

		// Its refresh token.
		mockMvc.perform(post("/auth/refresh").with(serverName(clinicA.host()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(clinicB.refreshToken())))
			.andExpect(status().isUnauthorized());

		// Its staff invitation.
		mockMvc.perform(post("/invitations/" + invitationForB + "/accept").with(serverName(clinicA.host()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"Dee Dentist","password":"%s"}
						""".formatted(PASSWORD)))
			.andExpect(status().isNotFound());

		// Its administrator, signing in here.
		mockMvc.perform(post("/auth/login").with(serverName(clinicA.host()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(clinicB.email(), PASSWORD)))
			.andExpect(status().isUnauthorized());

		// Its access token.
		mockMvc.perform(get("/members").with(serverName(clinicA.host())).header("Authorization", clinicB.bearer()))
			.andExpect(status().isForbidden());
	}

	private Probe probe(Clinic clinic) throws Exception {
		String body = mockMvc.perform(get("/__probe/native-read").with(serverName(clinic.host()))
				.header("Authorization", clinic.bearer()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return new Probe(JsonPath.read(body, "$.clinicSetting"), JsonPath.read(body, "$.emails"));
	}

	private String membershipId(Clinic clinic) throws Exception {
		String body = mockMvc.perform(get("/members").with(serverName(clinic.host()))
				.header("Authorization", clinic.bearer()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<List<String>>read(body, "$.content[*].id").getFirst();
	}

	private String invitationToken(Clinic clinic) throws Exception {
		String body = mockMvc.perform(post("/invitations").with(serverName(clinic.host()))
				.header("Authorization", clinic.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"invited-%s@example.com","role":"DENTIST"}
						""".formatted(UUID.randomUUID())))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private Clinic register() throws Exception {
		UUID suffix = UUID.randomUUID();
		String slug = "clinic-" + suffix;
		String email = "admin-" + suffix + "@example.com";

		String registration = mockMvc.perform(post("/auth/register").with(serverName("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(suffix)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		String login = mockMvc.perform(post("/auth/login").with(serverName(slug + ".localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		return new Clinic(UUID.fromString(JsonPath.read(registration, "$.clinicId")), slug, email,
				JsonPath.read(login, "$.accessToken"), JsonPath.read(login, "$.refreshToken"));
	}

	private static String registrationBody(UUID suffix) {
		return """
				{
				  "clinicName": "Clinic %s",
				  "slug": "clinic-%s",
				  "adminEmail": "admin-%s@example.com",
				  "adminPassword": "%s",
				  "adminFullName": "Ada Admin"
				}
				""".formatted(suffix, suffix, suffix, PASSWORD);
	}

	private static RequestPostProcessor serverName(String host) {
		return request -> {
			request.setServerName(host);
			return request;
		};
	}

	private record Clinic(UUID id, String slug, String email, String accessToken, String refreshToken) {

		String host() {
			return slug + ".localhost";
		}

		String bearer() {
			return "Bearer " + accessToken;
		}

	}

	private record Probe(String clinicSetting, List<String> emails) {
	}

	/**
	 * Reads a tenant-owned table with native SQL carrying no Clinic condition,
	 * inside a request's own transaction. Hibernate is not in this path, so
	 * anything filtering these rows is the database itself.
	 */
	@TestConfiguration
	@RestController
	static class NativeReadProbe {

		private final JdbcTemplate jdbcTemplate;

		NativeReadProbe(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
		}

		@GetMapping("/__probe/native-read")
		@Transactional(readOnly = true)
		public Map<String, Object> read() {
			return Map.of(
					"clinicSetting", jdbcTemplate.queryForObject(
							"SELECT coalesce(current_setting('app.clinic_id', true), '')", String.class),
					"emails", jdbcTemplate.queryForList("""
							SELECT u.email FROM membership m JOIN app_user u ON u.id = m.user_id
							""", String.class));
		}

	}

}
