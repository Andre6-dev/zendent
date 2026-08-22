package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.zendent.shared.tenancy.TenantContext;

/**
 * Issue #22: the token claim is the authoritative Clinic, a subdomain that
 * disagrees with it is refused, and the API is no longer open to anonymous
 * callers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticatedClinicContextIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@BeforeEach
	void clearClinic() {
		TenantContext.clear();
	}

	@Test
	void aValidTokenOnItsOwnSubdomainReportsTheCaller() throws Exception {
		Session session = signIn();

		mockMvc.perform(get("/me").with(serverName(session.slug() + ".localhost")).header("Authorization", session.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.clinicId").value(session.clinicId().toString()))
			.andExpect(jsonPath("$.email").value(session.email()))
			.andExpect(jsonPath("$.roles[0]").value("ADMIN"));
	}

	@Test
	void aTokenFromAnotherClinicIsRefusedOnThisSubdomain() throws Exception {
		Session clinicA = signIn();
		Session clinicB = signIn();

		mockMvc.perform(get("/me").with(serverName(clinicA.slug() + ".localhost")).header("Authorization", clinicB.bearer()))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void aRefusalFromTheFilterChainLooksLikeOneFromAController() throws Exception {
		Session clinicA = signIn();
		Session clinicB = signIn();

		String fromChain = mockMvc.perform(get("/me").with(serverName(clinicA.slug() + ".localhost"))
				.header("Authorization", clinicB.bearer()))
			.andExpect(status().isForbidden())
			.andReturn().getResponse().getContentAsString();
		// Registration on a Clinic subdomain is refused by the controller.
		String fromController = mockMvc.perform(post("/auth/register").with(serverName(clinicA.slug() + ".localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(UUID.randomUUID())))
			.andExpect(status().isForbidden())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<Integer>read(fromChain, "$.status"))
			.isEqualTo(JsonPath.<Integer>read(fromController, "$.status"));
		assertThat(JsonPath.<String>read(fromChain, "$.title"))
			.isEqualTo(JsonPath.<String>read(fromController, "$.title"));
	}

	@Test
	void theProbeRefusesAMissingMalformedOrExpiredToken() throws Exception {
		Session session = signIn();
		String host = session.slug() + ".localhost";

		mockMvc.perform(get("/me").with(serverName(host)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me").with(serverName(host)).header("Authorization", "Bearer not-a-token"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me").with(serverName(host))
				.header("Authorization", "Bearer " + expiredToken(session)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenFromAnUntrustedIssuerIsRefused() throws Exception {
		Session session = signIn();

		mockMvc.perform(get("/me").with(serverName(session.slug() + ".localhost"))
				.header("Authorization", "Bearer " + tokenFrom("not-zendent", session, Instant.now().plusSeconds(600))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void onboardingAndLoginRemainReachableWithoutAToken() throws Exception {
		UUID suffix = UUID.randomUUID();
		mockMvc.perform(post("/auth/register").with(serverName("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(suffix)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/auth/login").with(serverName("clinic-" + suffix + ".localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"admin-%s@example.com","password":"%s"}
						""".formatted(suffix, PASSWORD)))
			.andExpect(status().isOk());
	}

	@Test
	void anEndpointThatIsNotExplicitlyPublicRequiresASession() throws Exception {
		Session session = signIn();

		mockMvc.perform(get("/me").with(serverName(session.slug() + ".localhost")))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void theProbeDocumentsItsTagAndEveryStatusItCanReturn() throws Exception {
		String apiDocs = mockMvc.perform(get("/v3/api-docs").with(serverName("localhost")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(apiDocs, "$.paths./me.get.tags")).containsExactly("Session");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(apiDocs, "$.paths./me.get.responses").keySet())
			.containsExactlyInAnyOrder("200", "401", "403", "404");
		assertThat(JsonPath.<String>read(apiDocs, "$.paths./me.get.summary")).isNotBlank();
	}

	private String expiredToken(Session session) {
		return tokenFrom("zendent", session, Instant.now().minus(1, ChronoUnit.HOURS));
	}

	private String tokenFrom(String issuer, Session session, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.issuedAt(expiresAt.minusSeconds(900))
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.subject(UUID.randomUUID().toString())
			.claim("clinic_id", session.clinicId().toString())
			.claim("email", session.email())
			.claim("roles", List.of("ADMIN"))
			.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private Session signIn() throws Exception {
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

		return new Session(UUID.fromString(JsonPath.read(registration, "$.clinicId")), slug, email,
				JsonPath.read(login, "$.accessToken"));
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

	private record Session(UUID clinicId, String slug, String email, String accessToken) {

		String bearer() {
			return "Bearer " + accessToken;
		}

	}

}
