package com.fooddelivery.platform.security;

import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** Signature aside, an access token is accepted only with our issuer, audience and expiry. */
public final class JwtValidation {

  private JwtValidation() {}

  public static OAuth2TokenValidator<Jwt> validator(JwtProperties properties) {
    String issuer = Objects.requireNonNull(properties.issuer(), "fdp.security.jwt.issuer");
    String audience = Objects.requireNonNull(properties.audience(), "fdp.security.jwt.audience");
    OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
  }
}
