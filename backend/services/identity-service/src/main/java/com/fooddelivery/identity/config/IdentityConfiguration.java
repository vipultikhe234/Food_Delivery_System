package com.fooddelivery.identity.config;

import com.fooddelivery.identity.application.UserRegistered;
import com.fooddelivery.identity.infrastructure.persistence.SigningKeyStore;
import com.fooddelivery.identity.infrastructure.token.AccessTokenIssuer;
import com.fooddelivery.identity.infrastructure.token.SigningKey;
import com.fooddelivery.identity.infrastructure.token.SigningKeyRegistrar;
import com.fooddelivery.platform.events.EventTopics;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

  private static final Logger log = LoggerFactory.getLogger(IdentityConfiguration.class);

  /** Environments where a generated signing key is acceptable: a developer machine only. */
  static final Set<String> EPHEMERAL_KEY_ENVIRONMENTS = Set.of("local");

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Argon2id with the OWASP minimum: 19 MiB memory, 2 iterations, parallelism 1, 16-byte salt,
   * 32-byte hash (docs/09-security.md §2.1).
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
  }

  @Bean
  SigningKey signingKey(
      IdentityProperties properties, @Value("${fdp.environment:local}") String environment) {
    String pem = properties.jwt().signingKey();
    if (pem != null && !pem.isBlank()) {
      return SigningKey.fromPkcs8Pem(pem);
    }
    String env = environment.toLowerCase(Locale.ROOT);
    if (properties.jwt().ephemeralKey() && EPHEMERAL_KEY_ENVIRONMENTS.contains(env)) {
      log.warn(
          "JWT_SIGNING_KEY is not set: using a generated signing key. Tokens become invalid on"
              + " restart. Never use this outside a developer machine.");
      return SigningKey.generateEphemeral();
    }
    throw new IllegalStateException(
        "JWT_SIGNING_KEY is required (PKCS#8 PEM of an RSA key of at least 2048 bits). On a"
            + " developer machine JWT_EPHEMERAL_KEY=true generates a throwaway key instead.");
  }

  @Bean
  JwtEncoder jwtEncoder(SigningKey key) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key.jwk())));
  }

  @Bean
  SigningKeyRegistrar signingKeyRegistrar(SigningKey key, SigningKeyStore store, Clock clock) {
    return new SigningKeyRegistrar(key, store, clock);
  }

  @Bean
  AccessTokenIssuer accessTokenIssuer(
      JwtEncoder encoder,
      SigningKey key,
      TokenClaimsProperties claims,
      IdentityProperties properties) {
    return new AccessTokenIssuer(encoder, key, claims, properties.tokens());
  }

  @Bean
  NewTopic identityEvents(IdentityProperties properties) {
    return EventTopics.topic(
        UserRegistered.TOPIC, properties.events().partitions(), properties.events().replicas());
  }
}
