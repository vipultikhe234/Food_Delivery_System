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
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull Tokens tokens,
    @Valid @NotNull Events events,
    Bootstrap bootstrap) {

  public IdentityProperties {
    if (bootstrap == null) {
      bootstrap = new Bootstrap(null);
    }
  }

  /**
   * @param signingKey PKCS#8 PEM of the RSA signing key, from the secret manager
   * @param ephemeralKey generate a throwaway key when none is configured (developer machines only)
   */
  public record Jwt(String signingKey, boolean ephemeralKey) {}

  public record Tokens(@NotNull Duration accessTokenTtl, @NotNull Duration refreshTokenTtl) {}

  /**
   * @param auditPartitions partitions of audit.events.v1, which every service producing audit
   *     records declares
   */
  public record Events(
      @Min(1) int partitions, @Min(1) short replicas, @Min(1) int auditPartitions) {}

  /**
   * @param superAdmin e-mail address or phone number of an already registered account that becomes
   *     the first SUPER_ADMIN; ignored once any SUPER_ADMIN exists
   */
  public record Bootstrap(String superAdmin) {}
}
