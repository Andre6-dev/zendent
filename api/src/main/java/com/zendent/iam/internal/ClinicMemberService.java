package com.zendent.iam.internal;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.domain.Clinic;
import com.zendent.iam.domain.Membership;
import com.zendent.iam.mapper.MemberMapper;
import com.zendent.iam.web.MemberResponse;
import com.zendent.iam.web.SignedInMember;
import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.domain.PageResponse;
import com.zendent.shared.domain.NotFoundException;

/**
 * Reads the Memberships of the Clinic the request is scoped to.
 *
 * <p>No method here names a Clinic. {@code @TenantId} and row-level security
 * scope every Membership read to the active Clinic, so a Membership elsewhere is
 * invisible rather than filtered — which is also why a Membership in another
 * Clinic and an identifier that exists nowhere produce the same not-found
 * answer, with no code here having to arrange that.
 */
@Service
public class ClinicMemberService {

	private final MembershipRepository membershipRepository;
	private final ClinicRepository clinicRepository;
	private final MemberMapper mapper;

	ClinicMemberService(MembershipRepository membershipRepository, ClinicRepository clinicRepository,
			MemberMapper mapper) {
		this.membershipRepository = membershipRepository;
		this.clinicRepository = clinicRepository;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberResponse> listMembers(Pageable pageable) {
		return PageResponse.from(membershipRepository.findAll(pageable)).map(mapper::toResponse);
	}

	/**
	 * The names a screen can show the person whose session this is.
	 *
	 * <p>The Membership lookup is scoped to the active Clinic like every other
	 * read here, so a user id from another Clinic's token resolves to nothing.
	 * That half is the database's doing.
	 *
	 * <p>The Clinic read is not, and it is worth saying so rather than implying
	 * otherwise: {@code clinic} is not tenant-owned and carries no row-level
	 * security policy. What makes it safe is that the identifier comes from the
	 * Membership just found under the Clinic's own scoping, never from anything
	 * the caller supplied — so there is nothing here to substitute.
	 */
	@Transactional(readOnly = true)
	public SignedInMember describeSignedIn(UUID userId) {
		Membership membership = membershipRepository.findByUserId(userId)
			.orElseThrow(() -> new NotFoundException(ErrorMessages.MEMBER_NOT_FOUND));
		// A Membership without its Clinic is a broken foreign key, not a caller
		// asking for something that is not there.
		Clinic clinic = clinicRepository.findById(membership.clinicId())
			.orElseThrow(() -> new IllegalStateException(
					"Membership " + membership.id() + " names a Clinic that does not exist"));

		return new SignedInMember(membership.user().fullName(), clinic.name());
	}

	@Transactional(readOnly = true)
	public MemberResponse findMember(UUID memberId) {
		return membershipRepository.findById(memberId)
			.map(mapper::toResponse)
			.orElseThrow(() -> new NotFoundException(ErrorMessages.MEMBER_NOT_FOUND));
	}

}
