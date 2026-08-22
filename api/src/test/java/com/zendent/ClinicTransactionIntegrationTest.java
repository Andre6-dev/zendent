package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.zendent.shared.tenancy.TenantContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@ActiveProfiles("test")
class ClinicTransactionIntegrationTest {

	@Autowired
	private JdbcTemplate applicationJdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private PostgreSQLContainer postgresContainer;

	private UUID clinicA;
	private UUID clinicB;
	private UUID membershipA;
	private UUID membershipB;
	private UUID spareUser;
	private UUID roleId;

	@BeforeEach
	void seedMembershipsAsTableOwner() {
		var ownerJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
				postgresContainer.getJdbcUrl(),
				postgresContainer.getUsername(),
				postgresContainer.getPassword()));
		clinicA = UUID.randomUUID();
		clinicB = UUID.randomUUID();
		membershipA = UUID.randomUUID();
		membershipB = UUID.randomUUID();
		UUID userA = UUID.randomUUID();
		UUID userB = UUID.randomUUID();
		spareUser = UUID.randomUUID();
		roleId = ownerJdbcTemplate.queryForObject("SELECT id FROM role WHERE code = 'ADMIN'", UUID.class);

		ownerJdbcTemplate.update(
				"INSERT INTO clinic (id, name, slug, status) VALUES (?, 'Clinic A', ?, 'ACTIVE')",
				clinicA, "clinic-a-" + clinicA);
		ownerJdbcTemplate.update(
				"INSERT INTO clinic (id, name, slug, status) VALUES (?, 'Clinic B', ?, 'ACTIVE')",
				clinicB, "clinic-b-" + clinicB);
		ownerJdbcTemplate.update("""
				INSERT INTO app_user (id, email, password_hash, full_name, status)
				VALUES (?, ?, 'hash', 'User A', 'ACTIVE'),
				       (?, ?, 'hash', 'User B', 'ACTIVE'),
				       (?, ?, 'hash', 'Spare User', 'ACTIVE')
				""",
				userA, "user-a-" + userA + "@example.com",
				userB, "user-b-" + userB + "@example.com",
				spareUser, "spare-" + spareUser + "@example.com");
		ownerJdbcTemplate.update("""
				INSERT INTO membership (id, clinic_id, user_id, role_id, status)
				VALUES (?, ?, ?, ?, 'ACTIVE'), (?, ?, ?, ?, 'ACTIVE')
				""", membershipA, clinicA, userA, roleId, membershipB, clinicB, userB, roleId);
	}

	@AfterEach
	void clearClinicContext() {
		TenantContext.clear();
	}

	@Test
	void activeClinicIsPublishedBeforeNativeQueriesRun() {
		List<UUID> visibleMemberships = TenantContext.withClinic(clinicA,
				() -> transactionTemplate.execute(status -> applicationJdbcTemplate.queryForList(
						"SELECT id FROM membership ORDER BY id", UUID.class)));

		assertThat(visibleMemberships).containsExactly(membershipA);
		assertThat(TenantContext.get()).isEmpty();
	}

	@Test
	void activeClinicIsPublishedToEveryTransactionInTheSameScope() {
		List<String> publishedClinicIds = TenantContext.withClinic(clinicA, () -> List.of(
				transactionTemplate.execute(status -> currentDatabaseClinic()),
				transactionTemplate.execute(status -> currentDatabaseClinic())));

		assertThat(publishedClinicIds).containsExactly(clinicA.toString(), clinicA.toString());
	}

	@Test
	void transactionsForDifferentClinicsOnTheSamePoolSeeOnlyTheirOwnRows() {
		TransactionObservation clinicAObservation = observeTransactionFor(clinicA);
		TransactionObservation clinicBObservation = observeTransactionFor(clinicB);

		assertThat(clinicAObservation.connectionId()).isEqualTo(clinicBObservation.connectionId());
		assertThat(clinicAObservation.visibleMemberships()).containsExactly(membershipA);
		assertThat(clinicBObservation.visibleMemberships()).containsExactly(membershipB);
	}

	@Test
	void missingClinicContextDeniesReadsAndWritesWithoutAMissingSettingError() {
		List<UUID> visibleMemberships = transactionTemplate.execute(status -> applicationJdbcTemplate.queryForList(
				"SELECT id FROM membership", UUID.class));

		assertThat(visibleMemberships).isEmpty();
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> applicationJdbcTemplate.update("""
				INSERT INTO membership (id, clinic_id, user_id, role_id, status)
				VALUES (?, ?, ?, ?, 'ACTIVE')
				""", UUID.randomUUID(), clinicA, spareUser, roleId)))
				.isInstanceOf(DataAccessException.class)
				.rootCause()
				.hasMessageContaining("violates row-level security policy");
	}

	@Test
	void completedTransactionLeavesNoClinicResidueOnThePooledConnection() {
		Integer firstConnection = TenantContext.withClinic(clinicA,
				() -> transactionTemplate.execute(status -> applicationJdbcTemplate.queryForObject(
						"SELECT pg_backend_pid()", Integer.class)));

		var nextTransaction = transactionTemplate.execute(status -> new TransactionObservation(
				applicationJdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class),
				applicationJdbcTemplate.queryForList("SELECT id FROM membership", UUID.class)));

		assertThat(nextTransaction.connectionId()).isEqualTo(firstConnection);
		assertThat(nextTransaction.visibleMemberships()).isEmpty();
	}

	private String currentDatabaseClinic() {
		return applicationJdbcTemplate.queryForObject(
				"SELECT current_setting('app.clinic_id', true)", String.class);
	}

	private TransactionObservation observeTransactionFor(UUID clinicId) {
		return TenantContext.withClinic(clinicId, () -> transactionTemplate.execute(status -> new TransactionObservation(
				applicationJdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class),
				applicationJdbcTemplate.queryForList("SELECT id FROM membership ORDER BY id", UUID.class))));
	}

	private record TransactionObservation(Integer connectionId, List<UUID> visibleMemberships) {
	}

}
