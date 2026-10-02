package com.fooddelivery.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidationTest {

  private final OAuth2TokenValidator<Jwt> validator =
      JwtValidation.validator(
          new JwtProperties(
              "fooddelivery-identity",
              "fooddelivery-api",
              URI.create("http://identity-service/.well-known/jwks.json")));

  @Test
  void acceptsOurIssuerAndAudience() {
    assertThat(validator.validate(TestTokens.token(List.of(), List.of()).build()).hasErrors())
        .isFalse();
  }

  @Test
  void rejectsAnotherIssuerAudienceOrAnExpiredToken() {
    Jwt otherIssuer = TestTokens.token(List.of(), List.of()).issuer("someone-else").build();
    Jwt otherAudience =
        TestTokens.token(List.of(), List.of()).audience(List.of("internal")).build();
    Instant past = Instant.now().minusSeconds(3600);
    Jwt expired =
        TestTokens.token(List.of(), List.of())
            .issuedAt(past.minusSeconds(900))
            .expiresAt(past)
            .build();

    for (Jwt jwt : List.of(otherIssuer, otherAudience, expired)) {
      assertThat(validator.validate(jwt).hasErrors()).as(jwt.getClaims().toString()).isTrue();
    }
  }
}
