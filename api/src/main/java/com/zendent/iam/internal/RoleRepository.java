package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.Role;

interface RoleRepository extends JpaRepository<Role, UUID> {

	Optional<Role> findByCode(Role.Code code);

}
