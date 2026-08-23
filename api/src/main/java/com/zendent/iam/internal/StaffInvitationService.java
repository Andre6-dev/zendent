package com.zendent.iam.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.Membership;
import com.zendent.iam.domain.Role;
import com.zendent.iam.domain.StaffInvitation;
import com.zendent.iam.domain.User;
import com.zendent.iam.web.InvitationAcceptance;
import com.zendent.iam.web.InvitationRequest;
import com.zendent.iam.web.InvitationResponse;
import com.zendent.shared.domain.ConflictException;
import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.domain.NotFoundException;

/**
 * Issues and redeems offers of Membership.
 *
 * <p>Every read here is scoped to the active Clinic by {@code @TenantId} and by
 * row-level security. That is what makes an invitation unusable on another
 * Clinic's subdomain: it is not found there, rather than found and refused.
 *
 * <p>Redemption answers not-found for an invitation that is unknown, expired,
 * already accepted, or issued by another Clinic. Distinguishing those to an
 * unauthenticated caller would let someone probe for live tokens.
 */
@Service
public class StaffInvitationService {

	private final StaffInvitationRepository invitationRepository;
	private final MembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final SingleUseSecretPolicy secretPolicy;
	private final Duration timeToLive;

	StaffInvitationService(StaffInvitationRepository invitationRepository,
			MembershipRepository membershipRepository, UserRepository userRepository,
			RoleRepository roleRepository, PasswordEncoder passwordEncoder, SingleUseSecretPolicy secretPolicy,
			@Value("${zendent.invitation.ttl}") Duration timeToLive) {
		this.invitationRepository = invitationRepository;
		this.membershipRepository = membershipRepository;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.secretPolicy = secretPolicy;
		this.timeToLive = timeToLive;
	}

	@Transactional
	public InvitationResponse invite(InvitationRequest request, UUID clinicId, UUID invitedBy) {
		String email = normalize(request.email());
		// Caught here rather than left to the unique constraint at redemption:
		// an administrator should learn the person is already on the team now,
		// not after they follow a link that cannot work.
		if (alreadyAMember(email)) {
			throw new ConflictException(ErrorMessages.ALREADY_A_MEMBER);
		}
		Role role = roleRepository.findByCode(request.role())
			.orElseThrow(() -> new IllegalStateException("Role " + request.role() + " is not configured"));
		SingleUseSecretPolicy.MintedSecret token = secretPolicy.mint();

		StaffInvitation invitation = invitationRepository.save(new StaffInvitation(
				clinicId, email, role, token.hash(), invitedBy, Instant.now().plus(timeToLive)));

		return new InvitationResponse(invitation.id(), invitation.email(), role.code().name(),
				invitation.expiresAt(), token.value());
	}

	@Transactional
	public void accept(String token, InvitationAcceptance acceptance) {
		Instant now = Instant.now();
		StaffInvitation invitation = invitationRepository.findByTokenHash(secretPolicy.hash(token))
			.filter(candidate -> candidate.isRedeemableAt(now))
			.orElseThrow(() -> new NotFoundException(ErrorMessages.INVITATION_NOT_REDEEMABLE));

		User user = userRepository.findByEmail(invitation.email())
			.orElseGet(() -> userRepository.saveAndFlush(new User(invitation.email(),
					passwordEncoder.encode(acceptance.password()), acceptance.fullName().trim())));

		if (membershipRepository.findByUserId(user.id()).isPresent()) {
			throw new ConflictException(ErrorMessages.ALREADY_A_MEMBER);
		}
		membershipRepository.save(new Membership(invitation.clinicId(), user, invitation.role()));
		invitation.accept(now);
	}

	/**
	 * Tenant-scoped, so it can only ever see the active Clinic's Memberships —
	 * inviting someone who is a member of a different Clinic is not a conflict.
	 */
	private boolean alreadyAMember(String email) {
		return userRepository.findByEmail(email)
			.flatMap(user -> membershipRepository.findByUserId(user.id()))
			.isPresent();
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

}
