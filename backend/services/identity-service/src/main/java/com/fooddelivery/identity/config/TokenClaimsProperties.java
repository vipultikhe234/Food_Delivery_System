package com.fooddelivery.identity.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Issuer and audience shared with every token validator
 * (infrastructure/config-repo/application.yml, ADR-008). The gateway reads the same keys, so a
 * mismatch cannot go unnoticed in one place only.
 */
@Validated
@ConfigurationProperties("fdp.security.jwt")
public record TokenClaimsProperties(@NotBlank String issuer, @NotBlank String audience) {}
