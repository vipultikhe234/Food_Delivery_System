package com.fooddelivery.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway settings served by config-server. Missing values stop the gateway at startup
 * (REQ-PLAT-003 AC4).
 */
@Validated
@ConfigurationProperties("fdp.gateway")
public record GatewayProperties(@Valid @NotNull Cors cors, List<@Valid PublicPath> publicPaths) {

  public GatewayProperties {
    publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
  }

  public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {}

  /** A path that may be called without an access token; {@code method} null means any method. */
  public record PublicPath(@NotBlank String pattern, HttpMethod method) {}
}
