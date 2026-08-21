package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
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

	@Autowired
	private Flyway flyway;

	@Test
	void flywayAppliedV1BaselineMigration() {
		var appliedVersions = Arrays.stream(flyway.info().applied())
				.map(migration -> migration.getVersion().getVersion())
				.toList();

		assertThat(appliedVersions).contains("1");
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
