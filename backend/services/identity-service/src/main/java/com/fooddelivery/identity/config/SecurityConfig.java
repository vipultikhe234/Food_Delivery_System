package com.fooddelivery.identity.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Only the token endpoints and the JWKS are public; everything else is denied until the endpoint
 * that needs it is built (deny by default, REQ-AUTH-003).
 *
 * <p>CSRF protection stays on. The token endpoints are exempt because they authenticate with
 * credentials in the request body, never with cookies; the cookie-based web refresh will need the
 * double-submit token (REQ-SEC-002 AC4, KI-029).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  static final String[] TOKEN_ENDPOINTS = {
    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"
  };

  @Bean
  SecurityFilterChain identitySecurity(HttpSecurity http) throws Exception {
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
    RequestMatcher tokenEndpoints =
        new OrRequestMatcher(
            Arrays.stream(TOKEN_ENDPOINTS)
                .<RequestMatcher>map(path -> paths.matcher(HttpMethod.POST, path))
                .toList());

    return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.ignoringRequestMatchers(tokenEndpoints))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(tokenEndpoints)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        // sendError routes through the problem+json error controller of common-web.
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(
                        (request, response, ex) -> {
                          response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                          response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            response.sendError(HttpServletResponse.SC_FORBIDDEN)))
        .build();
  }
}
