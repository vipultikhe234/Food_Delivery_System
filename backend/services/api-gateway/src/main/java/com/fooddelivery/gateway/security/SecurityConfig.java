package com.fooddelivery.gateway.security;

import com.fooddelivery.gateway.config.GatewayProperties;
import com.fooddelivery.gateway.config.JwtProperties;
import com.fooddelivery.gateway.error.ErrorResponses;
import com.fooddelivery.platform.observability.correlation.CorrelationId;
import java.util.List;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Stateless JWT resource server for the edge (ADR-008, docs/09-security.md §2.2, §6.1). Only the
 * configured public paths are reachable without a token; permissions are enforced by each service.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

  static final String REFRESH_PATH = "/api/v1/auth/refresh";
  static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

  private static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  private static final List<String> ALLOWED_HEADERS =
      List.of(
          HttpHeaders.AUTHORIZATION,
          HttpHeaders.CONTENT_TYPE,
          HttpHeaders.ACCEPT,
          HttpHeaders.ACCEPT_LANGUAGE,
          HttpHeaders.IF_NONE_MATCH,
          CorrelationId.HEADER,
          "Idempotency-Key",
          "X-Client-App",
          "X-Client-Version",
          "traceparent");
  private static final List<String> EXPOSED_HEADERS =
      List.of(
          CorrelationId.HEADER,
          HttpHeaders.ETAG,
          HttpHeaders.LOCATION,
          HttpHeaders.RETRY_AFTER,
          "RateLimit-Limit",
          "RateLimit-Remaining",
          "RateLimit-Reset");

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      GatewayProperties properties,
      ReactiveJwtDecoder jwtDecoder,
      CorsConfigurationSource corsConfigurationSource) {
    ServerAuthenticationEntryPoint entryPoint =
        (exchange, ex) -> {
          exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
          return ErrorResponses.write(exchange, HttpStatus.UNAUTHORIZED);
        };
    ServerAccessDeniedHandler accessDenied =
        (exchange, ex) -> ErrorResponses.write(exchange, HttpStatus.FORBIDDEN);

    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .anonymous(ServerHttpSecurity.AnonymousSpec::disable)
        .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .headers(
            headers ->
                headers
                    .frameOptions(frame -> frame.mode(Mode.DENY))
                    .referrerPolicy(
                        referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                    // Written by EdgeHeadersWebFilter: TLS ends at the ingress, so the gateway
                    // sees plain HTTP and Spring would skip HSTS.
                    .hsts(ServerHttpSecurity.HeaderSpec.HstsSpec::disable))
        .authorizeExchange(
            exchanges -> {
              // Probes on the management port; actuator is not served on the public port.
              exchanges
                  .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                  .permitAll();
              for (GatewayProperties.PublicPath path : properties.publicPaths()) {
                if (path.method() == null) {
                  exchanges.pathMatchers(path.pattern()).permitAll();
                } else {
                  exchanges.pathMatchers(path.method(), path.pattern()).permitAll();
                }
              }
              exchanges.anyExchange().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDenied))
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
        .build();
  }

  /**
   * The JWKS URI names identity-service logically; the load-balancer filter resolves it through
   * discovery. Keys are fetched on the first token, so the gateway starts before identity-service.
   */
  @Bean
  ReactiveJwtDecoder jwtDecoder(
      JwtProperties jwt, ReactorLoadBalancerExchangeFilterFunction loadBalancer) {
    NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withJwkSetUri(jwt.jwksUri().toString())
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .webClient(WebClient.builder().filter(loadBalancer).build())
            .build();
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(jwt.audience()));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(jwt.issuer()), audience));
    return decoder;
  }

  /** Exact origins only; cookies (the refresh token) are allowed on the refresh path alone. */
  @Bean
  CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
    UrlBasedCorsConfigurationSource source =
        new UrlBasedCorsConfigurationSource(PathPatternParser.defaultInstance);
    source.registerCorsConfiguration(REFRESH_PATH, cors(properties, true));
    source.registerCorsConfiguration("/**", cors(properties, false));
    return source;
  }

  private static CorsConfiguration cors(GatewayProperties properties, boolean credentials) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.cors().allowedOrigins());
    config.setAllowedMethods(ALLOWED_METHODS);
    config.setAllowedHeaders(ALLOWED_HEADERS);
    config.setExposedHeaders(EXPOSED_HEADERS);
    config.setAllowCredentials(credentials);
    config.setMaxAge(3600L);
    return config;
  }
}
