package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DatabaseRolePrivilegesIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void applicationConnectsAsRestrictedRoleThatDoesNotOwnClinicScopedTables() {
		String currentUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
		Map<String, Object> roleAttributes = jdbcTemplate.queryForMap(
				"SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user");
		var clinicScopedTableOwners = jdbcTemplate.queryForList("""
				SELECT tableowner
				FROM pg_tables
				WHERE schemaname = 'public'
				  AND tablename IN ('membership', 'refresh_token', 'staff_invitation')
				ORDER BY tablename
				""", String.class);

		assertThat(currentUser).isEqualTo("zendent_app");
		assertThat(roleAttributes)
				.containsEntry("rolsuper", false)
				.containsEntry("rolbypassrls", false);
		assertThat(clinicScopedTableOwners)
				.hasSize(3)
				.containsOnly("zendent_owner");
	}

}
