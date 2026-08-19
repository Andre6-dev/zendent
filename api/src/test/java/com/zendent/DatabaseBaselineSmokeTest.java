package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Baseline smoke test for PKG-2.1's Database Schema Baseline requirement
 * (task 2.1.12): asserts the Spring context loads AND that Flyway's
 * {@code V1__init.sql} actually applied against the Testcontainers Postgres
 * instance — not just that the app boots.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DatabaseBaselineSmokeTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayAppliedV1BaselineMigration() {
		Integer historyRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true", Integer.class);

		assertThat(historyRows).isEqualTo(1);
	}

	@Test
	void v1SeededTheRoleCatalog() {
		var roleCodes = jdbcTemplate.queryForList("SELECT code FROM role ORDER BY code", String.class);

		assertThat(roleCodes).containsExactly("ADMIN", "DENTIST", "STAFF");
	}

	@Test
	void eventPublicationTableExistsForModulithRegistry() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'event_publication'",
				Integer.class);

		assertThat(tableCount).isEqualTo(1);
	}

}
