package com.fooddelivery.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** REQ-AUTH-001 AC2: Argon2id with the parameters of docs/09-security.md §2.1. */
class PasswordHashingTest {

  private final PasswordEncoder encoder = new IdentityConfiguration().passwordEncoder();

  @Test
  void passwordsAreHashedWithArgon2idAndTheOwaspParameters() {
    String hash = encoder.encode("correct horse battery");

    assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain("correct horse");
    assertThat(encoder.matches("correct horse battery", hash)).isTrue();
    assertThat(encoder.matches("correct horse battery!", hash)).isFalse();
  }

  @Test
  void theSamePasswordGetsADifferentSaltEachTime() {
    assertThat(encoder.encode("correct horse battery"))
        .isNotEqualTo(encoder.encode("correct horse battery"));
  }
}
