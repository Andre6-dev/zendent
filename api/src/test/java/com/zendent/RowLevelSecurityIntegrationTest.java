package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RowLevelSecurityIntegrationTest {

	@Autowired
	private JdbcTemplate applicationJdbcTemplate;

	@Autowired
	private DataSource applicationDataSource;

	@Autowired
	private PostgreSQLContainer postgresContainer;

	private JdbcTemplate ownerJdbcTemplate;
	private Fixture fixture;

	@BeforeEach
	void seedFixtureAsTableOwner() {
		ownerJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
				postgresContainer.getJdbcUrl(),
				postgresContainer.getUsername(),
				postgresContainer.getPassword()));

		UUID clinicA = UUID.randomUUID();
		UUID clinicB = UUID.randomUUID();
		UUID userA = UUID.randomUUID();
		UUID userB = UUID.randomUUID();
		UUID spareUser = UUID.randomUUID();
		UUID roleId = ownerJdbcTemplate.queryForObject(
				"SELECT id FROM role WHERE code = 'ADMIN'", UUID.class);

		insertClinic(clinicA, "clinic-a-" + clinicA);
		insertClinic(clinicB, "clinic-b-" + clinicB);
		insertUser(userA, "a-" + userA + "@example.com");
		insertUser(userB, "b-" + userB + "@example.com");
		insertUser(spareUser, "spare-" + spareUser + "@example.com");

		fixture = new Fixture(
				clinicA,
				clinicB,
				roleId,
				spareUser,
				new ClinicScopedRows(
						UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), UUID.randomUUID()));

		ownerJdbcTemplate.update("""
				INSERT INTO membership (id, clinic_id, user_id, role_id, status)
				VALUES (?, ?, ?, ?, 'ACTIVE'), (?, ?, ?, ?, 'ACTIVE')
				""",
				fixture.rows().membershipA(), clinicA, userA, roleId,
				fixture.rows().membershipB(), clinicB, userB, roleId);
		ownerJdbcTemplate.update("""
				INSERT INTO refresh_token (id, user_id, clinic_id, token_hash, jti, expires_at)
				VALUES (?, ?, ?, ?, ?, now() + interval '1 day'),
				       (?, ?, ?, ?, ?, now() + interval '1 day')
				""",
				fixture.rows().refreshTokenA(), userA, clinicA, "hash-" + fixture.rows().refreshTokenA(), "jti-a",
				fixture.rows().refreshTokenB(), userB, clinicB, "hash-" + fixture.rows().refreshTokenB(), "jti-b");
		ownerJdbcTemplate.update("""
				INSERT INTO staff_invitation
				    (id, clinic_id, email, role_id, token, status, invited_by, expires_at)
				VALUES (?, ?, ?, ?, ?, 'PENDING', ?, now() + interval '1 day'),
				       (?, ?, ?, ?, ?, 'PENDING', ?, now() + interval '1 day')
				""",
				fixture.rows().staffInvitationA(), clinicA, "invite-a@example.com", roleId,
				"token-" + fixture.rows().staffInvitationA(), userA,
				fixture.rows().staffInvitationB(), clinicB, "invite-b@example.com", roleId,
				"token-" + fixture.rows().staffInvitationB(), userB);
	}

	@Test
	void everyClinicScopedTableHasForcedRowLevelSecurityAndAPolicy() {
		var clinicScopedTables = applicationJdbcTemplate.queryForList("""
				SELECT c.relname AS table_name,
				       c.relrowsecurity AS rls_enabled,
				       c.relforcerowsecurity AS rls_forced,
				       EXISTS (
				           SELECT 1
				           FROM pg_policy p
				           WHERE p.polrelid = c.oid
				       ) AS has_policy
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				JOIN pg_attribute a ON a.attrelid = c.oid
				WHERE n.nspname = 'public'
				  AND c.relkind IN ('r', 'p', 'f')
				  AND a.attname = 'clinic_id'
				  AND NOT a.attisdropped
				ORDER BY c.relname
				""");

		assertThat(clinicScopedTables)
				.isNotEmpty()
				.allSatisfy(row -> assertThat(row)
						.as("RLS configuration for %s", row.get("table_name"))
						.containsAllEntriesOf(enabledAndForcedWithPolicy()));
	}

	@Test
	void clinicContextRestrictsNativeReadsIncludingQueriesByKnownPrimaryKey() throws SQLException {
		try (Connection connection = applicationTransaction(fixture.clinicA())) {
			assertThat(queryIds(connection, "membership"))
					.containsExactly(fixture.rows().membershipA());
			assertThat(queryIds(connection, "refresh_token"))
					.containsExactly(fixture.rows().refreshTokenA());
			assertThat(queryIds(connection, "staff_invitation"))
					.containsExactly(fixture.rows().staffInvitationA());

			assertThat(countById(connection, "membership", fixture.rows().membershipB())).isZero();
			assertThat(countById(connection, "refresh_token", fixture.rows().refreshTokenB())).isZero();
			assertThat(countById(connection, "staff_invitation", fixture.rows().staffInvitationB())).isZero();
		}
	}

	@Test
	void missingClinicContextDeniesAllNativeReadsAndInserts() throws SQLException {
		try (Connection connection = applicationTransaction(null)) {
			assertThat(queryIds(connection, "membership")).isEmpty();
			assertThat(queryIds(connection, "refresh_token")).isEmpty();
			assertThat(queryIds(connection, "staff_invitation")).isEmpty();
		}

		assertInsertRejected(null, """
				INSERT INTO membership (id, clinic_id, user_id, role_id, status)
				VALUES (?, ?, ?, ?, 'ACTIVE')
				""", UUID.randomUUID(), fixture.clinicA(), fixture.spareUser(), fixture.roleId());
		assertInsertRejected(null, """
				INSERT INTO refresh_token (id, user_id, clinic_id, token_hash, jti, expires_at)
				VALUES (?, ?, ?, ?, 'missing-context', now() + interval '1 day')
				""", UUID.randomUUID(), fixture.spareUser(), fixture.clinicA(), "missing-" + UUID.randomUUID());
		assertInsertRejected(null, """
				INSERT INTO staff_invitation
				    (id, clinic_id, email, role_id, token, status, invited_by, expires_at)
				VALUES (?, ?, 'missing@example.com', ?, ?, 'PENDING', ?, now() + interval '1 day')
				""", UUID.randomUUID(), fixture.clinicA(), fixture.roleId(),
				"missing-" + UUID.randomUUID(), fixture.spareUser());
	}

	@Test
	void clinicContextRejectsRowsOwnedByAnotherClinic() {
		assertInsertRejected(fixture.clinicA(), """
				INSERT INTO membership (id, clinic_id, user_id, role_id, status)
				VALUES (?, ?, ?, ?, 'ACTIVE')
				""", UUID.randomUUID(), fixture.clinicB(), fixture.spareUser(), fixture.roleId());
		assertInsertRejected(fixture.clinicA(), """
				INSERT INTO refresh_token (id, user_id, clinic_id, token_hash, jti, expires_at)
				VALUES (?, ?, ?, ?, 'wrong-clinic', now() + interval '1 day')
				""", UUID.randomUUID(), fixture.spareUser(), fixture.clinicB(), "wrong-" + UUID.randomUUID());
		assertInsertRejected(fixture.clinicA(), """
				INSERT INTO staff_invitation
				    (id, clinic_id, email, role_id, token, status, invited_by, expires_at)
				VALUES (?, ?, 'wrong@example.com', ?, ?, 'PENDING', ?, now() + interval '1 day')
				""", UUID.randomUUID(), fixture.clinicB(), fixture.roleId(),
				"wrong-" + UUID.randomUUID(), fixture.spareUser());
	}

	@Test
	void clinicContextCannotUpdateOrDeleteAnotherClinicsRows() throws SQLException {
		try (Connection connection = applicationTransaction(fixture.clinicA())) {
			assertThat(executeUpdate(connection,
					"UPDATE membership SET status = 'SUSPENDED' WHERE id = ?", fixture.rows().membershipB()))
					.isZero();
			assertThat(executeUpdate(connection,
					"UPDATE refresh_token SET revoked_at = now() WHERE id = ?", fixture.rows().refreshTokenB()))
					.isZero();
			assertThat(executeUpdate(connection,
					"UPDATE staff_invitation SET status = 'EXPIRED' WHERE id = ?", fixture.rows().staffInvitationB()))
					.isZero();

			assertThat(executeUpdate(connection,
					"DELETE FROM membership WHERE id = ?", fixture.rows().membershipB())).isZero();
			assertThat(executeUpdate(connection,
					"DELETE FROM refresh_token WHERE id = ?", fixture.rows().refreshTokenB())).isZero();
			assertThat(executeUpdate(connection,
					"DELETE FROM staff_invitation WHERE id = ?", fixture.rows().staffInvitationB())).isZero();
		}

		assertThat(ownerJdbcTemplate.queryForObject(
				"SELECT status FROM membership WHERE id = ?", String.class, fixture.rows().membershipB()))
				.isEqualTo("ACTIVE");
		assertThat(ownerJdbcTemplate.queryForObject(
				"SELECT revoked_at IS NULL FROM refresh_token WHERE id = ?", Boolean.class,
				fixture.rows().refreshTokenB())).isTrue();
		assertThat(ownerJdbcTemplate.queryForObject(
				"SELECT status FROM staff_invitation WHERE id = ?", String.class,
				fixture.rows().staffInvitationB())).isEqualTo("PENDING");
	}

	@Test
	void globallyScopedTablesRemainReadableWithoutClinicContext() throws SQLException {
		try (Connection connection = applicationTransaction(null)) {
			assertThat(countRows(connection, "clinic")).isPositive();
			assertThat(countRows(connection, "app_user")).isPositive();
			assertThat(countRows(connection, "role")).isPositive();
			assertThat(countRows(connection, "event_publication")).isZero();
		}
	}

	private Map<String, Object> enabledAndForcedWithPolicy() {
		return Map.of(
				"rls_enabled", true,
				"rls_forced", true,
				"has_policy", true);
	}

	private void insertClinic(UUID id, String slug) {
		ownerJdbcTemplate.update(
				"INSERT INTO clinic (id, name, slug, status) VALUES (?, 'Test Clinic', ?, 'ACTIVE')",
				id, slug);
	}

	private void insertUser(UUID id, String email) {
		ownerJdbcTemplate.update("""
				INSERT INTO app_user (id, email, password_hash, full_name, status)
				VALUES (?, ?, 'hash', 'Test User', 'ACTIVE')
				""", id, email);
	}

	private Connection applicationTransaction(UUID clinicId) throws SQLException {
		Connection connection = applicationDataSource.getConnection();
		connection.setAutoCommit(false);
		if (clinicId != null) {
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT set_config('app.clinic_id', ?, true)")) {
				statement.setString(1, clinicId.toString());
				statement.execute();
			}
		}
		return connection;
	}

	private List<UUID> queryIds(Connection connection, String table) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM " + table + " ORDER BY id");
				ResultSet resultSet = statement.executeQuery()) {
			var ids = new java.util.ArrayList<UUID>();
			while (resultSet.next()) {
				ids.add(resultSet.getObject("id", UUID.class));
			}
			return ids;
		}
	}

	private int countById(Connection connection, String table, UUID id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT COUNT(*) FROM " + table + " WHERE id = ?")) {
			statement.setObject(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		}
	}

	private int countRows(Connection connection, String table) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
				ResultSet resultSet = statement.executeQuery()) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	private int executeUpdate(Connection connection, String sql, UUID id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setObject(1, id);
			return statement.executeUpdate();
		}
	}

	private void assertInsertRejected(UUID clinicId, String sql, Object... arguments) {
		assertThatThrownBy(() -> {
			try (Connection connection = applicationTransaction(clinicId);
					PreparedStatement statement = connection.prepareStatement(sql)) {
				for (int index = 0; index < arguments.length; index++) {
					statement.setObject(index + 1, arguments[index]);
				}
				statement.executeUpdate();
			}
		})
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("violates row-level security policy");
	}

	private record Fixture(
			UUID clinicA,
			UUID clinicB,
			UUID roleId,
			UUID spareUser,
			ClinicScopedRows rows) {
	}

	private record ClinicScopedRows(
			UUID membershipA,
			UUID membershipB,
			UUID refreshTokenA,
			UUID refreshTokenB,
			UUID staffInvitationA,
			UUID staffInvitationB) {
	}

}
