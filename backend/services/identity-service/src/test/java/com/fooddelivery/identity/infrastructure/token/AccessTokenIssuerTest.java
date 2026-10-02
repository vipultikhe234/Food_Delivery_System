package com.fooddelivery.identity.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.identity.config.IdentityProperties;
import com.fooddelivery.identity.infrastructure.persistence.RoleStore.Grants;
import com.fooddelivery.identity.infrastructure.persistence.RoleStore.Scope;
import com.fooddelivery.platform.security.AccessScope;
import com.fooddelivery.platform.security.AuthenticatedUser;
import com.fooddelivery.platform.security.JwtProperties;
import com.fooddelivery.platform.security.JwtValidation;
import com.fooddelivery.platform.security.UserJwtConverter;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * REQ-AUTH-001 AC5 (token side): a validator that holds only the public key verifies the token and
 * its claims, as the gateway does with the JWKS; REQ-AUTH-003: services read the grants with the
 * common-security converter.
 */
class AccessTokenIssuerTest {

  private static final JwtProperties CLAIMS =
      new JwtProperties(
          "fooddelivery-identity",
          "fooddelivery-api",
          URI.create("http://identity-service/.well-known/jwks.json"));

  private final SigningKey key = SigningKey.generateEphemeral();
  private final AccessTokenIssuer issuer =
      new AccessTokenIssuer(
          new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key.jwk()))),
          key,
          CLAIMS,
          new IdentityProperties.Tokens(Duration.ofMinutes(15), Duration.ofDays(30)));

  @Test
  void tokensVerifyWithThePublicKeyAndCarryTheDesignedClaims() throws Exception {
    UUID user = UUID.randomUUID();
    UUID session = UUID.randomUUID();
    UUID branch = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    AccessTokenIssuer.AccessToken token =
        issuer.issue(
            user,
            session,
            new Grants(
                List.of("CUSTOMER"), List.of("ORDER_CREATE"), List.of(new Scope("BRANCH", branch))),
            now);

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.jwk().toRSAPublicKey()).build();
    decoder.setJwtValidator(JwtValidation.validator(CLAIMS));
    Jwt jwt = decoder.decode(token.value());

    assertThat(jwt.getSubject()).isEqualTo(user.toString());
    assertThat(jwt.getClaimAsString("sid")).isEqualTo(session.toString());
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
    assertThat(jwt.getClaimAsStringList("perms")).containsExactly("ORDER_CREATE");
    assertThat(jwt.<List<Map<String, Object>>>getClaim("scopes"))
        .containsExactly(Map.of("type", "BRANCH", "id", branch.toString()));
    assertThat(jwt.getId()).isNotBlank();
    assertThat(jwt.getIssuedAt()).isEqualTo(now);
    assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());

    JWSObject jws = JWSObject.parse(token.value());
    assertThat(jws.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
    assertThat(jws.getHeader().getKeyID()).isEqualTo(key.kid());

    AuthenticatedUser principal = new UserJwtConverter().convert(jwt).getPrincipal();
    assertThat(principal.userId()).isEqualTo(user);
    assertThat(principal.sessionId()).isEqualTo(session);
    assertThat(principal.hasPermission("ORDER_CREATE")).isTrue();
    assertThat(principal.inScope(AccessScope.Type.BRANCH, branch)).isTrue();
  }

  @Test
  void aTokenSignedByAnotherKeyIsRejected() throws Exception {
    String token =
        issuer
            .issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Grants(List.of("CUSTOMER"), List.of(), List.of()),
                Instant.now())
            .value();
    NimbusJwtDecoder other =
        NimbusJwtDecoder.withPublicKey(SigningKey.generateEphemeral().jwk().toRSAPublicKey())
            .build();

    assertThatThrownBy(() -> other.decode(token)).isInstanceOf(JwtException.class);
  }
}
