package com.fooddelivery.configserver;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Clients authenticate with HTTP Basic (REQ-PLAT-003). Credentials come only from
 * CONFIG_SERVER_USER / CONFIG_SERVER_PASSWORD; probes on the management port stay open.
 *
 * <p>CSRF protection stays on: browsers resend cached Basic credentials, and config clients only
 * send GET requests, which CSRF checks never block.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain configServerSecurity(HttpSecurity http) throws Exception {
    return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults())
        .formLogin(form -> form.disable())
        .build();
  }
}
