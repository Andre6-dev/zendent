package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.zendent.iam.internal.PasswordResetDeliveryRequested;
import com.zendent.shared.tenancy.TenantContext;

/** Issue #40: request a password reset and receive it through Mailpit. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class PasswordResetRequestIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery-staple";
	private static final String GENERIC_RESPONSE =
			"If an account exists for that email, a password reset link will be sent.";
	private static final Pattern RESET_TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PostgreSQLContainer postgresContainer;

	@Autowired
	@Qualifier("mailpitContainer")
	private GenericContainer<?> mailpitContainer;

	@Autowired
	private ApplicationEvents applicationEvents;

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private JdbcTemplate ownerJdbcTemplate;

	@BeforeEach
	void connectAsTableOwnerAndEmptyMailbox() throws Exception {
		ownerJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
				postgresContainer.getJdbcUrl(),
				postgresContainer.getUsername(),
				postgresContainer.getPassword()));
		TenantContext.clear();
		HttpResponse<String> response = httpClient.send(
				HttpRequest.newBuilder(mailpitUri("/api/v1/messages"))
					.method("DELETE", HttpRequest.BodyPublishers.ofString("{}"))
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
	}

	@Test
	void existingMemberReceivesAResetLinkWhileOnlyTheTokenHashIsStored() throws Exception {
		Clinic clinic = registerClinic();

		mockMvc.perform(post("/auth/forgot-password").with(serverName(clinic.slug() + ".localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s"}
						""".formatted(clinic.adminEmail())))
			.andExpect(status().isAccepted())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value(GENERIC_RESPONSE));

		Map<String, Object> storedToken = ownerJdbcTemplate.queryForMap("""
				SELECT id, clinic_id, token_hash
				FROM password_reset_token
				WHERE user_id = ?
				""", clinic.adminId());
		assertThat(storedToken.get("clinic_id")).isEqualTo(clinic.id());
		assertThat(storedToken.get("token_hash").toString()).matches("[0-9a-f]{64}");

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			String message = latestMessage();
			assertThat(JsonPath.<String>read(message, "$.To[0].Address"))
				.isEqualTo(clinic.adminEmail());
			String text = JsonPath.read(message, "$.Text");
			Matcher matcher = RESET_TOKEN.matcher(text);
			assertThat(matcher.find()).isTrue();
			assertThat(storedToken.get("token_hash")).isNotEqualTo(matcher.group(1));
			String serializedEvent = ownerJdbcTemplate.queryForObject("""
					SELECT serialized_event
					FROM event_publication
					WHERE event_type LIKE '%PasswordResetDeliveryRequested'
					  AND serialized_event LIKE ?
					""", String.class, "%" + storedToken.get("id") + "%");
			assertThat(serializedEvent)
				.doesNotContain(matcher.group(1))
				.doesNotContain(clinic.adminEmail());
		});
	}

	@Test
	void unknownAddressHasTheSameResponseWithoutAStoredTokenOrDeliveryEvent() throws Exception {
		Clinic clinic = registerClinic();

		MvcResult known = requestReset(clinic.slug(), clinic.adminEmail(), "")
			.andExpect(status().isAccepted())
			.andReturn();
		long storedAfterKnownRequest = tokenCount();
		long eventsAfterKnownRequest = applicationEvents.stream(PasswordResetDeliveryRequested.class).count();

		MvcResult unknown = requestReset(clinic.slug(), "nobody-" + UUID.randomUUID() + "@example.com", "")
			.andExpect(status().isAccepted())
			.andReturn();

		assertThat(unknown.getResponse().getContentAsString())
			.isEqualTo(known.getResponse().getContentAsString());
		assertThat(tokenCount()).isEqualTo(storedAfterKnownRequest);
		assertThat(applicationEvents.stream(PasswordResetDeliveryRequested.class).count())
			.isEqualTo(eventsAfterKnownRequest);
	}

	@Test
	void requestBodyCannotSelectAnotherClinic() throws Exception {
		Clinic clinicA = registerClinic();
		Clinic clinicB = registerClinic();

		requestReset(clinicA.slug(), clinicB.adminEmail(), """
				,"clinicId":"%s","slug":"%s"
				""".formatted(clinicB.id(), clinicB.slug()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.message").value(GENERIC_RESPONSE));

		Integer tokensForClinicBMember = ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM password_reset_token WHERE user_id = ?",
				Integer.class, clinicB.adminId());
		assertThat(tokensForClinicBMember).isZero();
		assertThat(applicationEvents.stream(PasswordResetDeliveryRequested.class)).isEmpty();
	}

	@Test
	void requestRequiresAResolvableClinicHostAndValidEmail() throws Exception {
		Clinic clinic = registerClinic();

		mockMvc.perform(post("/auth/forgot-password").with(serverName("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/auth/forgot-password").with(serverName("missing.localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\"}"))
			.andExpect(status().isNotFound());
		requestReset(clinic.slug(), "not-an-email", "")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(tokenCount()).isZero();
		assertThat(applicationEvents.stream(PasswordResetDeliveryRequested.class)).isEmpty();
	}

	@Test
	void forgotPasswordEndpointDocumentsItsTagAndEveryStatusItCanReturn() throws Exception {
		String apiDocs = mockMvc.perform(get("/v3/api-docs").with(serverName("localhost")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<java.util.List<String>>read(
				apiDocs, "$.paths./auth/forgot-password.post.tags"))
			.containsExactly("Authentication");
		assertThat(JsonPath.<java.util.Map<String, ?>>read(
				apiDocs, "$.paths./auth/forgot-password.post.responses").keySet())
			.containsExactlyInAnyOrder("202", "400", "403", "404");
		assertThat(JsonPath.<String>read(
				apiDocs, "$.paths./auth/forgot-password.post.summary")).isNotBlank();
	}

	private String latestMessage() throws Exception {
		HttpResponse<String> response = httpClient.send(
				HttpRequest.newBuilder(mailpitUri("/api/v1/message/latest")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		return response.body();
	}

	private URI mailpitUri(String path) {
		return URI.create("http://%s:%d%s".formatted(
				mailpitContainer.getHost(),
				mailpitContainer.getMappedPort(TestcontainersConfiguration.MAILPIT_HTTP_PORT),
				path));
	}

	private org.springframework.test.web.servlet.ResultActions requestReset(
			String slug, String email, String extraFields) throws Exception {
		return mockMvc.perform(post("/auth/forgot-password").with(serverName(slug + ".localhost"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email":"%s"%s}
					""".formatted(email, extraFields)));
	}

	private long tokenCount() {
		return ownerJdbcTemplate.queryForObject("SELECT count(*) FROM password_reset_token", Long.class);
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
						""".formatted(suffix, slug, email, PASSWORD)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		UUID clinicId = UUID.fromString(JsonPath.read(response, "$.clinicId"));
		UUID adminId = ownerJdbcTemplate.queryForObject(
				"SELECT id FROM app_user WHERE email = ?", UUID.class, email);
		return new Clinic(clinicId, slug, email, adminId);
	}

	private static RequestPostProcessor serverName(String host) {
		return request -> {
			request.setServerName(host);
			return request;
		};
	}

	private record Clinic(UUID id, String slug, String adminEmail, UUID adminId) {
	}

}
