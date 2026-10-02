package com.fooddelivery.identity.domain;

import java.util.Optional;

/**
 * Length rules from docs/09-security.md §2.1 (NIST 800-63B: no composition rules). The upper bound
 * keeps hashing cost bounded. The breached-password check is not implemented yet (KI-028).
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 10;
  public static final int MAX_LENGTH = 128;

  private PasswordPolicy() {}

  /** Returns the reason the password is rejected, or empty when it is acceptable. */
  public static Optional<String> violation(String password) {
    if (password == null) {
      return Optional.of("Password is required.");
    }
    int length = password.codePointCount(0, password.length());
    if (length < MIN_LENGTH) {
      return Optional.of("Password must be at least " + MIN_LENGTH + " characters long.");
    }
    if (length > MAX_LENGTH) {
      return Optional.of("Password must be at most " + MAX_LENGTH + " characters long.");
    }
    return Optional.empty();
  }
}
