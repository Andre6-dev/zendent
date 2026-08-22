package com.zendent.iam.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.Membership;

interface MembershipRepository extends JpaRepository<Membership, UUID> {
}
