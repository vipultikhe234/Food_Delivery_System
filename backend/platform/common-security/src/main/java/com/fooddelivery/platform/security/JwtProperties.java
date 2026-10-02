package com.fooddelivery.platform.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access-token settings shared by the issuer and every validator (config-repo {@code
 * application.yml}, ADR-008).
 *
 * @param jwksUri may name a service ({@code http://identity-service/...}); it is resolved through
 *     discovery when the service has a load balancer
 */
@ConfigurationProperties("fdp.security.jwt")
public record JwtProperties(String issuer, String audience, URI jwksUri) {}
