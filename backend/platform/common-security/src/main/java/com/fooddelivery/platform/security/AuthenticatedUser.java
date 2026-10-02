package com.fooddelivery.platform.security;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The caller of a request, as stated by a verified access token (docs/09-security.md §2.2).
 * Permissions answer "may this kind of user do this"; ownership and scope checks against the target
 * resource are still required in the application layer (docs/09 §3.4).
 */
public record AuthenticatedUser(
    UUID userId,
    UUID sessionId,
    Set<String> roles,
    Set<String> permissions,
    List<AccessScope> scopes) {

  public AuthenticatedUser {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(sessionId, "sessionId");
    roles = Set.copyOf(roles);
    permissions = Set.copyOf(permissions);
    scopes = List.copyOf(scopes);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean hasPermission(String permission) {
    return permissions.contains(permission);
  }

  public boolean inScope(AccessScope.Type type, UUID id) {
    return scopes.contains(new AccessScope(type, id));
  }

  public Set<UUID> scopeIds(AccessScope.Type type) {
    return scopes.stream()
        .filter(scope -> scope.type() == type)
        .map(AccessScope::id)
        .collect(Collectors.toUnmodifiableSet());
  }
}
