package com.fooddelivery.identity.infrastructure.persistence;

import com.fooddelivery.identity.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** E-mail arguments must already be lower-case (see {@code Identifier}). */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByPhone(String phone);

  boolean existsByEmail(String email);

  boolean existsByPhone(String phone);

  /**
   * Bulk update without a version check: concurrent logins of one user from two devices must not
   * fail each other with an optimistic-lock conflict.
   */
  @Modifying
  @Query("UPDATE User u SET u.lastLoginAt = :at WHERE u.id = :id")
  int recordLogin(@Param("id") UUID id, @Param("at") Instant at);

  /** Replaces the hash after a login when the hashing parameters have changed. */
  @Modifying
  @Query("UPDATE User u SET u.passwordHash = :hash, u.updatedAt = :at WHERE u.id = :id")
  int rehashPassword(@Param("id") UUID id, @Param("hash") String hash, @Param("at") Instant at);
}
