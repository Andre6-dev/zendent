package com.zendent.shared.tenancy;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class DatabaseRoleStartupGuard implements SmartInitializingSingleton {

	private final JdbcTemplate jdbcTemplate;

	DatabaseRoleStartupGuard(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	public void afterSingletonsInstantiated() {
		RolePrivileges role = jdbcTemplate.queryForObject("""
				SELECT rolname, rolsuper, rolbypassrls
				FROM pg_roles
				WHERE rolname = current_user
				""", (resultSet, rowNumber) -> new RolePrivileges(
				resultSet.getString("rolname"),
				resultSet.getBoolean("rolsuper"),
				resultSet.getBoolean("rolbypassrls")));
		List<String> ownedClinicScopedTables = jdbcTemplate.queryForList("""
				SELECT DISTINCT c.relname
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				JOIN pg_attribute a ON a.attrelid = c.oid
				WHERE n.nspname = 'public'
				  AND c.relkind IN ('r', 'p', 'f')
				  AND a.attname = 'clinic_id'
				  AND NOT a.attisdropped
				  AND pg_get_userbyid(c.relowner) = current_user
				ORDER BY c.relname
				""", String.class);

		var rlsBypassReasons = new ArrayList<String>();
		if (role.superuser()) {
			rlsBypassReasons.add("SUPERUSER");
		}
		if (role.bypassRls()) {
			rlsBypassReasons.add("BYPASSRLS");
		}
		if (!ownedClinicScopedTables.isEmpty()) {
			rlsBypassReasons.add("owns Clinic-scoped tables " + ownedClinicScopedTables);
		}

		if (!rlsBypassReasons.isEmpty()) {
			throw new IllegalStateException("Database role '%s' is unsafe for application traffic: %s. "
					.formatted(role.name(), String.join("; ", rlsBypassReasons))
					+ "Row-level security cannot be guaranteed.");
		}
	}

	private record RolePrivileges(String name, boolean superuser, boolean bypassRls) {
	}

}
