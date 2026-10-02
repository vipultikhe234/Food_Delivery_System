package com.fooddelivery.platform.security;

import java.util.Objects;
import java.util.UUID;

/** A restaurant (brand) or branch (outlet) the user holds a role for ({@code scopes} claim). */
public record AccessScope(Type type, UUID id) {

  public enum Type {
    RESTAURANT,
    BRANCH
  }

  public AccessScope {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(id, "id");
  }
}
