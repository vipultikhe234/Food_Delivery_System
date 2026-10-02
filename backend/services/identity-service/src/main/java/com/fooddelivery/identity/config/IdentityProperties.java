package com.fooddelivery.identity.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** identity-service settings (infrastructure/config-repo/identity-service.yml). */
@Validated
@ConfigurationProperties("fdp.identity")
public record IdentityProperties(
    @Valid @NotNull Jwt jwt, @Valid @NotNull Tokens tokens, @Valid @NotNull Events events) {

  /**
   * @param signingKey PKCS#8 PEM of the RSA signing key, from the secret manager
   * @param ephemeralKey generate a throwaway key when none is configured (developer machines only)
   */
  public record Jwt(String signingKey, boolean ephemeralKey) {}

  public record Tokens(@NotNull Duration accessTokenTtl, @NotNull Duration refreshTokenTtl) {}

  public record Events(@Min(1) int partitions, @Min(1) short replicas) {}
}
