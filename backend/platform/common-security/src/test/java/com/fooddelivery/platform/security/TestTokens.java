package com.fooddelivery.platform.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Access tokens as identity-service shapes them, for decoder stubs. */
public final class TestTokens {

  public static final UUID USER = UUID.fromString("01920000-0000-7000-8000-00000000a001");
  public static final UUID SESSION = UUID.fromString("01920000-0000-7000-8000-00000000b001");
  public static final UUID BRANCH = UUID.fromString("01920000-0000-7000-8000-00000000c001");
  public static final UUID RESTAURANT = UUID.fromString("01920000-0000-7000-8000-00000000d001");

  private TestTokens() {}

  public static Jwt.Builder token(List<String> roles, List<String> permissions) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuer("fooddelivery-identity")
        .audience(List.of("fooddelivery-api"))
        .subject(USER.toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(900))
        .claim("sid", SESSION.toString())
        .claim("roles", roles)
        .claim("perms", permissions)
        .claim(
            "scopes",
            List.of(
                Map.of("type", "BRANCH", "id", BRANCH.toString()),
                Map.of("type", "RESTAURANT", "id", RESTAURANT.toString())));
  }
}
