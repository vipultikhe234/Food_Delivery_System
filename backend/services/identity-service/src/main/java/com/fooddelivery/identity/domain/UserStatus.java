package com.fooddelivery.identity.domain;

/** Account states (docs/06-database-design.md §2.1, {@code ck_users_status}). */
public enum UserStatus {
  PENDING_VERIFICATION,
  ACTIVE,
  LOCKED,
  BLOCKED,
  DELETED
}
