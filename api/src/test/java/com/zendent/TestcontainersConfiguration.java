package com.zendent;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	private static final String DATABASE_NAME = "zendent";
	private static final String OWNER_USERNAME = "zendent_owner";
	private static final String OWNER_PASSWORD = "zendent_owner";
	private static final String APPLICATION_USERNAME = "zendent_app";
	private static final String APPLICATION_PASSWORD = "zendent_app";
	private static final int MAILPIT_SMTP_PORT = 1025;
	static final int MAILPIT_HTTP_PORT = 8025;

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
	GenericContainer<?> mailpitContainer() {
		return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.30.6"))
			.withExposedPorts(MAILPIT_SMTP_PORT, MAILPIT_HTTP_PORT)
			.waitingFor(Wait.forHttp("/api/v1/info").forPort(MAILPIT_HTTP_PORT));
	}

	@Bean
	DynamicPropertyRegistrar mailProperties(
			@Qualifier("mailpitContainer") GenericContainer<?> mailpitContainer) {
		return registry -> {
			registry.add("spring.mail.host", mailpitContainer::getHost);
			registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(MAILPIT_SMTP_PORT));
		};
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
