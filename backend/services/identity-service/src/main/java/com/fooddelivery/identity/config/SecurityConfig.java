package com.fooddelivery.identity.config;

import com.fooddelivery.platform.security.ResourceServerSecurity;
import com.fooddelivery.platform.security.UserJwtConverter;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The token endpoints and the JWKS are public; admin endpoints need a token and a permission
 * declared on each method; everything else is denied until the endpoint that needs it is built
 * (deny by default, REQ-AUTH-003).
 *
 * <p>The token endpoints are also exempt from CSRF because they authenticate with credentials in
 * the request body, never with cookies; the cookie-based web refresh will need the double-submit
 * token (REQ-SEC-002 AC4, KI-029).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  static final String[] TOKEN_ENDPOINTS = {
    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"
  };

  @Bean
  SecurityFilterChain identitySecurity(
      HttpSecurity http, JwtDecoder jwtDecoder, UserJwtConverter converter) throws Exception {
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
    RequestMatcher tokenEndpoints =
        new OrRequestMatcher(
            Arrays.stream(TOKEN_ENDPOINTS)
                .<RequestMatcher>map(path -> paths.matcher(HttpMethod.POST, path))
                .toList());

    return ResourceServerSecurity.apply(http, jwtDecoder, converter)
        .csrf(csrf -> csrf.ignoringRequestMatchers(tokenEndpoints))
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
                    .requestMatchers("/api/v1/admin/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
