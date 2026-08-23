package com.zendent;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

import com.jayway.jsonpath.JsonPath;

/** Proves outbound mail end to end through the real SMTP and Mailpit APIs. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MailInfrastructureIntegrationTest {

	@Autowired
	private JavaMailSender mailSender;

	@Autowired
	@Qualifier("mailpitContainer")
	private GenericContainer<?> mailpitContainer;

	@Test
	void aSentMessageCanBeReadBackFromMailpit() throws Exception {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom("noreply@zendent.test");
		message.setTo("dentist@example.test");
		message.setSubject("Password recovery");
		message.setText("Follow this link to recover access to your Clinic.");

		mailSender.send(message);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://%s:%d/api/v1/message/latest".formatted(
					mailpitContainer.getHost(),
					mailpitContainer.getMappedPort(TestcontainersConfiguration.MAILPIT_HTTP_PORT))))
			.GET()
			.build();
		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(response.body(), "$.To[0].Address"))
			.isEqualTo("dentist@example.test");
		assertThat(JsonPath.<String>read(response.body(), "$.Text"))
			.contains("recover access to your Clinic");
	}

}
