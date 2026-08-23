package com.zendent.iam.mapper;

import org.springframework.stereotype.Component;

import com.zendent.iam.domain.Membership;
import com.zendent.iam.web.MemberResponse;

/**
 * Written by hand rather than generated.
 *
 * <p>MapStruct maps <em>into</em> entities happily — {@code ClinicOnboardingMapper}
 * does — because it writes through constructors. Reading back out is the problem:
 * its default accessor strategy only recognises {@code getX()}, and this
 * codebase's entities expose {@code x()}. Teaching it otherwise means an
 * {@code AccessorNamingStrategy} on the annotation-processor path, which has to
 * be compiled before the processor runs and therefore cannot live in this module.
 */
@Component
public class MemberMapper {

	public MemberResponse toResponse(Membership membership) {
		return new MemberResponse(
				membership.id(),
				membership.user().id(),
				membership.user().email(),
				membership.user().fullName(),
				membership.role().code().name(),
				membership.status().name(),
				membership.createdAt());
	}

}
