package com.fooddelivery.platform.persistence.entity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Fills {@code created_by} / {@code updated_by} with the authenticated user or service principal.
 * Principal names that are not UUIDs (anonymous, tests) leave the columns empty.
 */
public class SecurityContextAuditor implements AuditorAware<UUID> {

  @Override
  public Optional<UUID> getCurrentAuditor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    return parse(authentication.getName());
  }

  static Optional<UUID> parse(String name) {
    try {
      return Optional.of(UUID.fromString(name));
    } catch (IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }
}
