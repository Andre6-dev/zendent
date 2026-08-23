package com.zendent.iam.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.zendent.iam.domain.Clinic;
import com.zendent.iam.domain.User;

/** Sends reset instructions after the request transaction commits. */
@Component
class PasswordResetMailDelivery {

	private final JavaMailSender mailSender;
	private final UserRepository userRepository;
	private final ClinicRepository clinicRepository;
	private final SingleUseSecretPolicy secretPolicy;
	private final String sender;
	private final String linkTemplate;

	PasswordResetMailDelivery(JavaMailSender mailSender, UserRepository userRepository,
			ClinicRepository clinicRepository, SingleUseSecretPolicy secretPolicy,
			@Value("${zendent.mail.from}") String sender,
			@Value("${zendent.password-reset.link-template}") String linkTemplate) {
		this.mailSender = mailSender;
		this.userRepository = userRepository;
		this.clinicRepository = clinicRepository;
		this.secretPolicy = secretPolicy;
		this.sender = sender;
		this.linkTemplate = linkTemplate;
	}

	@ApplicationModuleListener
	void deliver(PasswordResetDeliveryRequested request) {
		String recipient = userRepository.findById(request.userId())
			.map(User::email)
			.orElseThrow(() -> new IllegalStateException("The reset recipient no longer exists"));
		String clinicSlug = clinicRepository.findById(request.clinicId())
			.map(Clinic::slug)
			.orElseThrow(() -> new IllegalStateException("The reset Clinic no longer exists"));
		String token = secretPolicy.deriveForPasswordReset(request.resetTokenId()).value();
		String resetLink = linkTemplate.formatted(clinicSlug, token);
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(sender);
		message.setTo(recipient);
		message.setSubject("Reset your Zendenta password");
		message.setText("Reset your password using this single-use link:\n\n" + resetLink);
		mailSender.send(message);
	}

}
