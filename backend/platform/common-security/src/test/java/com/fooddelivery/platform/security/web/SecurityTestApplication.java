package com.fooddelivery.platform.security.web;

import com.fooddelivery.platform.security.AccessScope;
import com.fooddelivery.platform.security.AuthenticatedUser;
import com.fooddelivery.platform.security.CurrentUser;
import com.fooddelivery.platform.security.ResourceServerSecurity;
import com.fooddelivery.platform.security.TestTokens;
import com.fooddelivery.platform.security.UserJwtConverter;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** A minimal service using the library the way real services do. */
@SpringBootConfiguration
@EnableWebSecurity
public class SecurityTestApplication {

  /** Token "customer" holds ORDER_VIEW and ORDER_CREATE; "partner" holds ORDER_VIEW only. */
  @Bean
  JwtDecoder jwtDecoder() {
    return token ->
        switch (token) {
          case "customer" ->
              TestTokens.token(List.of("CUSTOMER"), List.of("ORDER_VIEW", "ORDER_CREATE")).build();
          case "partner" ->
              TestTokens.token(List.of("DELIVERY_PARTNER"), List.of("ORDER_VIEW")).build();
          case "malformed" -> TestTokens.token(List.of(), List.of()).subject("not-a-uuid").build();
          default -> throw new BadJwtException("invalid token");
        };
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtDecoder jwtDecoder, UserJwtConverter converter) throws Exception {
    return ResourceServerSecurity.apply(http, jwtDecoder, converter)
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/public")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }

  @RestController
  static class SampleController {

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    Map<String, Object> orders(@AuthenticationPrincipal AuthenticatedUser user) {
      return Map.of(
          "userId", user.userId().toString(),
          "branches", user.scopeIds(AccessScope.Type.BRANCH).size());
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    Map<String, Object> create() {
      return Map.of("createdBy", CurrentUser.get().userId().toString());
    }

    @GetMapping("/public")
    @PermitAll
    String publicInfo() {
      return "ok";
    }
  }
}
