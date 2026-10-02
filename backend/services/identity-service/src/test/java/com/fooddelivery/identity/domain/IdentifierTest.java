package com.fooddelivery.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.identity.domain.Identifier.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierTest {

  @Test
  void emailAddressesAreTrimmedAndLowerCased() {
    assertThat(Identifier.email("  Asha.Rao@Example.COM "))
        .contains(new Identifier(Type.EMAIL, "asha.rao@example.com"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "asha", "@example.com", "asha@", "a@b@c", "as ha@example.com"})
  void malformedEmailAddressesAreRejected(String raw) {
    assertThat(Identifier.email(raw)).isEmpty();
  }

  @Test
  void phoneNumbersMustBeE164() {
    assertThat(Identifier.phone(" +919876543210 "))
        .contains(new Identifier(Type.PHONE, "+919876543210"));
    assertThat(Identifier.phone("9876543210")).isEmpty();
    assertThat(Identifier.phone("+0123456789")).isEmpty();
    assertThat(Identifier.phone("+91 98765 43210")).isEmpty();
    assertThat(Identifier.phone("+1234567890123456")).as("more than 15 digits").isEmpty();
  }

  @Test
  void loginIdentifiersAreClassifiedByTheAtSign() {
    assertThat(Identifier.parse("Asha@example.com")).map(Identifier::type).contains(Type.EMAIL);
    assertThat(Identifier.parse("+919876543210")).map(Identifier::type).contains(Type.PHONE);
    assertThat(Identifier.parse("asha")).isEmpty();
    assertThat(Identifier.parse(null)).isEmpty();
  }
}
