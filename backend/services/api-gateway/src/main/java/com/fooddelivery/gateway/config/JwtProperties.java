package com.fooddelivery.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access-token validation settings shared with identity-service (ADR-008). The JWKS URI uses the
 * logical service name and is resolved through discovery.
 */
@Validated
@ConfigurationProperties("fdp.security.jwt")
public record JwtProperties(
    @NotBlank String issuer, @NotBlank String audience, @NotNull URI jwksUri) {}
