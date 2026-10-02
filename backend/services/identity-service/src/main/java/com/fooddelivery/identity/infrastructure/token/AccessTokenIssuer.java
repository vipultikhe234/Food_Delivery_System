package com.fooddelivery.identity.infrastructure.token;

import com.fooddelivery.identity.config.IdentityProperties;
import com.fooddelivery.identity.config.TokenClaimsProperties;
import com.fooddelivery.identity.infrastructure.persistence.RoleStore.Grants;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Issues RS256 access tokens with the claims of docs/09-security.md §2.2. */
public class AccessTokenIssuer {

  public record AccessToken(String value, Instant expiresAt) {}

  private final JwtEncoder encoder;
  private final SigningKey key;
  private final TokenClaimsProperties claims;
  private final IdentityProperties.Tokens tokens;

  public AccessTokenIssuer(
      JwtEncoder encoder,
      SigningKey key,
      TokenClaimsProperties claims,
      IdentityProperties.Tokens tokens) {
    this.encoder = encoder;
    this.key = key;
    this.claims = claims;
    this.tokens = tokens;
  }

  public AccessToken issue(UUID userId, UUID sessionId, Grants grants, Instant now) {
    Instant expiresAt = now.plus(tokens.accessTokenTtl());
    List<Map<String, String>> scopes =
        grants.scopes().stream()
            .map(scope -> Map.of("type", scope.type(), "id", scope.id().toString()))
            .toList();
    JwtClaimsSet claimsSet =
        JwtClaimsSet.builder()
            .issuer(claims.issuer())
            .audience(List.of(claims.audience()))
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("sid", sessionId.toString())
            .claim("roles", grants.roles())
            .claim("perms", grants.permissions())
            .claim("scopes", scopes)
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.kid()).build();
    String value = encoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
    return new AccessToken(value, expiresAt);
  }
}
