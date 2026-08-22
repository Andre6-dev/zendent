package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zendent.iam.domain.User;

interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

}
