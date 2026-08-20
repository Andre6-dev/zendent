package com.zendent;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	private static final String DATABASE_NAME = "zendent";
	private static final String OWNER_USERNAME = "zendent_owner";
	private static final String OWNER_PASSWORD = "zendent_owner";
	private static final String APPLICATION_USERNAME = "zendent_app";
	private static final String APPLICATION_PASSWORD = "zendent_app";

	@Bean
	@ServiceConnection(type = FlywayConnectionDetails.class)
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
				.withDatabaseName(DATABASE_NAME)
				.withUsername(OWNER_USERNAME)
				.withPassword(OWNER_PASSWORD)
				.withInitScript("db/init/00-create-application-role.sql");
	}

	@Bean
	JdbcConnectionDetails applicationConnectionDetails(PostgreSQLContainer postgresContainer) {
		return new JdbcConnectionDetails() {

			@Override
			public String getUsername() {
				return APPLICATION_USERNAME;
			}

			@Override
			public String getPassword() {
				return APPLICATION_PASSWORD;
			}

			@Override
			public String getJdbcUrl() {
				return postgresContainer.getJdbcUrl();
			}

		};
	}

}
