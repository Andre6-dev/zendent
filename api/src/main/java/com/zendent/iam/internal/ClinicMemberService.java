package com.zendent.iam.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zendent.iam.mapper.MemberMapper;
import com.zendent.iam.web.MemberResponse;
import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.domain.NotFoundException;

/**
 * Reads the Memberships of the Clinic the request is scoped to.
 *
 * <p>Neither method names a Clinic. {@code @TenantId} and row-level security
 * scope both reads to the active Clinic, so a Membership elsewhere is invisible
 * rather than filtered — which is also why a Membership in another Clinic and an
 * identifier that exists nowhere produce the same not-found answer, with no code
 * here having to arrange that.
 */
@Service
public class ClinicMemberService {

	private final MembershipRepository membershipRepository;
	private final MemberMapper mapper;

	ClinicMemberService(MembershipRepository membershipRepository, MemberMapper mapper) {
		this.membershipRepository = membershipRepository;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> listMembers() {
		return membershipRepository.findAll().stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public MemberResponse findMember(UUID memberId) {
		return membershipRepository.findById(memberId)
			.map(mapper::toResponse)
			.orElseThrow(() -> new NotFoundException(ErrorMessages.MEMBER_NOT_FOUND));
	}

}
