package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class DatabaseRoleStartupGuardIntegrationTest {

	private static final String OWNER_USERNAME = "zendent_owner";
	private static final String OWNER_PASSWORD = "zendent_owner";
	private static final String APPLICATION_USERNAME = "zendent_app";

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
			DockerImageName.parse("postgres:17-alpine"))
			.withDatabaseName("zendent")
			.withUsername(OWNER_USERNAME)
			.withPassword(OWNER_PASSWORD)
			.withInitScript("db/init/00-create-application-role.sql");

	@Test
	void applicationRefusesToStartWithOwnerCredentials() {
		assertThatThrownBy(() -> {
			try (ConfigurableApplicationContext ignored = startApplicationAs(OWNER_USERNAME, OWNER_PASSWORD)) {
				// Startup itself is the behavior under test.
			}
		})
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Database role 'zendent_owner'")
				.hasMessageContaining("SUPERUSER")
				.hasMessageContaining("BYPASSRLS")
				.hasMessageContaining("owns Clinic-scoped tables [membership, refresh_token, staff_invitation]");
	}

	@Test
	void applicationStartsNormallyWithRestrictedRole() {
		try (ConfigurableApplicationContext context = startApplicationAs(APPLICATION_USERNAME, "zendent_app")) {
			assertThat(context.isActive()).isTrue();
		}
	}

	private ConfigurableApplicationContext startApplicationAs(String username, String password) {
		return new SpringApplicationBuilder(ApiApplication.class)
				.web(WebApplicationType.NONE)
				.profiles("test")
				.properties(
						"spring.main.banner-mode=off",
						"spring.main.register-shutdown-hook=false",
						"spring.datasource.url=" + POSTGRES.getJdbcUrl(),
						"spring.datasource.username=" + username,
						"spring.datasource.password=" + password,
						"spring.flyway.url=" + POSTGRES.getJdbcUrl(),
						"spring.flyway.user=" + OWNER_USERNAME,
						"spring.flyway.password=" + OWNER_PASSWORD,
						"spring.flyway.placeholders.applicationRole=" + APPLICATION_USERNAME)
				.run();
	}

}
