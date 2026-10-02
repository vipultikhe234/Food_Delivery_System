package com.fooddelivery.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The common part of every service's filter chain (docs/09-security.md §3.1): stateless bearer
 * tokens, no browser login mechanisms, and 401/403 rendered as problem+json by the error controller
 * of common-web. The service then adds its own {@code authorizeHttpRequests} rules.
 *
 * <pre>{@code
 * return ResourceServerSecurity.apply(http, jwtDecoder, converter)
 *     .authorizeHttpRequests(auth -> auth
 *         .requestMatchers("/actuator/health/**").permitAll()
 *         .anyRequest().authenticated())
 *     .build();
 * }</pre>
 *
 * <p>CSRF protection stays on for requests that could carry ambient credentials: it is skipped only
 * when the request has a bearer token (browsers never attach one by themselves) or no cookies at
 * all.
 */
public final class ResourceServerSecurity {

  private static final String BEARER_PREFIX = "Bearer ";

  private ResourceServerSecurity() {}

  public static HttpSecurity apply(
      HttpSecurity http, JwtDecoder jwtDecoder, UserJwtConverter converter) throws Exception {
    AuthenticationEntryPoint entryPoint =
        (request, response, ex) -> {
          response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        };
    AccessDeniedHandler accessDenied =
        (request, response, ex) -> response.sendError(HttpServletResponse.SC_FORBIDDEN);

    return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(
            csrf -> csrf.ignoringRequestMatchers(ResourceServerSecurity::withoutAmbientCredentials))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDenied))
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied));
  }

  static boolean withoutAmbientCredentials(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    boolean bearer =
        authorization != null
            && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    return bearer || request.getHeader(HttpHeaders.COOKIE) == null;
  }
}
