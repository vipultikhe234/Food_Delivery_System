package com.fooddelivery.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Startup rules for the signing key: configured, or generated on a developer machine only. */
class SigningKeyConfigurationTest {

  private final IdentityConfiguration configuration = new IdentityConfiguration();

  private static IdentityProperties properties(String pem, boolean ephemeral) {
    return new IdentityProperties(
        new IdentityProperties.Jwt(pem, ephemeral),
        new IdentityProperties.Tokens(Duration.ofMinutes(15), Duration.ofDays(30)),
        new IdentityProperties.Events(1, (short) 1));
  }

  @Test
  void withoutAKeyTheServiceRefusesToStart() {
    assertThatIllegalStateException()
        .isThrownBy(() -> configuration.signingKey(properties("", false), "local"))
        .withMessageContaining("JWT_SIGNING_KEY is required");
  }

  @Test
  void aGeneratedKeyIsAllowedOnlyLocally() {
    assertThat(configuration.signingKey(properties(null, true), "local").ephemeral()).isTrue();

    for (String environment : new String[] {"dev", "staging", "prod"}) {
      assertThatIllegalStateException()
          .isThrownBy(() -> configuration.signingKey(properties(null, true), environment));
    }
  }
}
