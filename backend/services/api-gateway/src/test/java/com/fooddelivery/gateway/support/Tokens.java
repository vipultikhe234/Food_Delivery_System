package com.fooddelivery.gateway.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Builds RS256 access tokens shaped like identity-service's (docs/09-security.md §2.2). */
public final class Tokens {

  public static final String ISSUER = "fooddelivery-identity";
  public static final String AUDIENCE = "fooddelivery-api";

  private Tokens() {}

  public static RSAKey newKey(String kid) {
    try {
      return new RSAKeyGenerator(2048).keyID(kid).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String valid(RSAKey key, String subject) {
    return sign(key, claims(subject, ISSUER, AUDIENCE, Instant.now().plus(Duration.ofMinutes(15))));
  }

  public static JWTClaimsSet claims(
      String subject, String issuer, String audience, Instant expiry) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer(issuer)
        .subject(subject)
        .audience(audience)
        .issueTime(Date.from(now.minus(Duration.ofMinutes(1))))
        .notBeforeTime(Date.from(now.minus(Duration.ofMinutes(1))))
        .expirationTime(Date.from(expiry))
        .jwtID(UUID.randomUUID().toString())
        .claim("sid", UUID.randomUUID().toString())
        .claim("roles", List.of("CUSTOMER"))
        .build();
  }

  public static String sign(RSAKey key, JWTClaimsSet claims) {
    try {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .keyID(key.getKeyID())
                  .type(JOSEObjectType.JWT)
                  .build(),
              claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
