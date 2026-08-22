package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.zendent.shared.events.ClinicCreatedEvent;
import com.zendent.shared.tenancy.TenantContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class ClinicOnboardingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate applicationJdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private PostgreSQLContainer postgresContainer;

	@Autowired
	private ApplicationEvents applicationEvents;

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
	void apexRegistrationCreatesOneClinicUserAndAdminMembership() throws Exception {
		Registration registration = registration();

		String response = mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registration.json()))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.clinicId").isNotEmpty())
			.andReturn().getResponse().getContentAsString();

		UUID clinicId = UUID.fromString(JsonPath.read(response, "$.clinicId"));
		Map<String, Object> created = ownerJdbcTemplate.queryForMap("""
				SELECT c.id AS clinic_id, u.id AS user_id, r.code AS role_code, m.status AS membership_status
				FROM clinic c
				JOIN membership m ON m.clinic_id = c.id
				JOIN app_user u ON u.id = m.user_id
				JOIN role r ON r.id = m.role_id
				WHERE c.slug = ? AND u.email = ?
				""", registration.slug(), registration.email());

		assertThat(created)
				.containsEntry("clinic_id", clinicId)
				.containsEntry("role_code", "ADMIN")
				.containsEntry("membership_status", "ACTIVE");
		assertThat(count("clinic", "slug", registration.slug())).isOne();
		assertThat(count("app_user", "email", registration.email())).isOne();
		assertThat(applicationEvents.stream(ClinicCreatedEvent.class)
				.filter(event -> event.clinicId().equals(clinicId)))
				.hasSize(1);
		assertThat(TenantContext.get()).isEmpty();

		String pooledSetting = transactionTemplate.execute(status -> applicationJdbcTemplate.queryForObject(
				"SELECT current_setting('app.clinic_id', true)", String.class));
		assertThat(pooledSetting).isEmpty();
	}

	@Test
	void callerSuppliedClinicHintsCannotRedirectTheMembership() throws Exception {
		UUID existingClinic = UUID.randomUUID();
		ownerJdbcTemplate.update(
				"INSERT INTO clinic (id, name, slug, status) VALUES (?, 'Existing', ?, 'ACTIVE')",
				existingClinic, "existing-" + existingClinic);
		Registration registration = registration();

		String bodyWithClinicHint = registration.json().replace("{", "{\"clinicId\":\"" + existingClinic + "\",");
		mockMvc.perform(post("/auth/register")
				.queryParam("clinicId", existingClinic.toString())
				.header("X-Tenant-Slug", "existing-" + existingClinic)
				.contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithClinicHint))
			.andExpect(status().isCreated());

		UUID membershipClinic = ownerJdbcTemplate.queryForObject("""
				SELECT m.clinic_id
				FROM membership m
				JOIN app_user u ON u.id = m.user_id
				WHERE u.email = ?
				""", UUID.class, registration.email());
		UUID createdClinic = ownerJdbcTemplate.queryForObject(
				"SELECT id FROM clinic WHERE slug = ?", UUID.class, registration.slug());

		assertThat(membershipClinic).isEqualTo(createdClinic).isNotEqualTo(existingClinic);
	}

	@Test
	void registrationFromAClinicSubdomainIsRejected() throws Exception {
		Registration registration = registration();

		mockMvc.perform(post("/auth/register")
				.header("Host", "existing.localhost")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registration.json()))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(count("clinic", "slug", registration.slug())).isZero();
		assertThat(count("app_user", "email", registration.email())).isZero();
		assertThat(applicationEvents.stream(ClinicCreatedEvent.class)).isEmpty();
	}

	@Test
	void duplicateRegistrationReturnsConflictWithoutCreatingPartialDataOrAnotherEvent() throws Exception {
		Registration registration = registration();
		mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registration.json()))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registration.json()))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		UUID clinicId = ownerJdbcTemplate.queryForObject(
				"SELECT id FROM clinic WHERE slug = ?", UUID.class, registration.slug());
		assertThat(count("clinic", "slug", registration.slug())).isOne();
		assertThat(count("app_user", "email", registration.email())).isOne();
		assertThat(ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM membership WHERE clinic_id = ?", Integer.class, clinicId)).isOne();
		assertThat(applicationEvents.stream(ClinicCreatedEvent.class)
				.filter(event -> event.clinicId().equals(clinicId)))
				.hasSize(1);
	}

	@Test
	void failureAfterClinicInsertRollsBackTheWholeRegistration() throws Exception {
		Registration registration = registration();
		ownerJdbcTemplate.update("""
				INSERT INTO app_user (id, email, password_hash, full_name, status)
				VALUES (?, ?, 'existing-hash', 'Existing User', 'ACTIVE')
				""", UUID.randomUUID(), registration.email());

		mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registration.json()))
			.andExpect(status().isConflict());

		assertThat(count("clinic", "slug", registration.slug())).isZero();
		assertThat(count("app_user", "email", registration.email())).isOne();
		assertThat(ownerJdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM membership m
				JOIN app_user u ON u.id = m.user_id
				WHERE u.email = ?
				""", Integer.class, registration.email())).isZero();
		assertThat(applicationEvents.stream(ClinicCreatedEvent.class)).isEmpty();
		assertThat(TenantContext.get()).isEmpty();
	}

	@Test
	void invalidRegistrationReturnsValidationProblemWithoutCreatingAnything() throws Exception {
		mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.clinicName").exists())
			.andExpect(jsonPath("$.errors.slug").exists())
			.andExpect(jsonPath("$.errors.adminEmail").exists())
			.andExpect(jsonPath("$.errors.adminPassword").exists())
			.andExpect(jsonPath("$.errors.adminFullName").exists());
	}

	private int count(String table, String column, String value) {
		return ownerJdbcTemplate.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
	}

	private static Registration registration() {
		UUID suffix = UUID.randomUUID();
		return new Registration(
				"Clinic " + suffix,
				"clinic-" + suffix,
				"admin-" + suffix + "@example.com",
				"correct-horse-battery-staple",
				"Ada Admin");
	}

	private record Registration(String clinicName, String slug, String email, String password, String fullName) {

		String json() {
			return """
					{
					  "clinicName": "%s",
					  "slug": "%s",
					  "adminEmail": "%s",
					  "adminPassword": "%s",
					  "adminFullName": "%s"
					}
					""".formatted(clinicName, slug, email, password, fullName);
		}

	}

}
