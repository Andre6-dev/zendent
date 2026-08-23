package com.zendent.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.zendent.iam.domain.User;

interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select candidate from User candidate where candidate.email = :email")
	Optional<User> findByEmailForCredentialCheck(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select candidate from User candidate where candidate.id = :id")
	Optional<User> findByIdForCredentialCheck(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select candidate from User candidate where candidate.id = :id")
	Optional<User> findByIdForCredentialChange(@Param("id") UUID id);

}
