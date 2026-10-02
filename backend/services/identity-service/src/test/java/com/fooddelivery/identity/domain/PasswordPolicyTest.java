package com.fooddelivery.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  @Test
  void lengthIsTheOnlyRule() {
    assertThat(PasswordPolicy.violation("aaaaaaaaaa"))
        .as("10 characters, no composition rules")
        .isEmpty();
    assertThat(PasswordPolicy.violation("short pwd")).isPresent();
    assertThat(PasswordPolicy.violation(null)).isPresent();
  }

  @Test
  void lengthCountsCharactersNotUtf16Units() {
    String tenEmoji = "\uD83C\uDF55".repeat(10);
    assertThat(PasswordPolicy.violation(tenEmoji)).isEmpty();
    assertThat(PasswordPolicy.violation("\uD83C\uDF55".repeat(9))).isPresent();
  }

  @Test
  void overlongPasswordsAreRejectedBeforeHashing() {
    assertThat(PasswordPolicy.violation("a".repeat(PasswordPolicy.MAX_LENGTH))).isEmpty();
    assertThat(PasswordPolicy.violation("a".repeat(PasswordPolicy.MAX_LENGTH + 1))).isPresent();
  }
}
