package com.fooddelivery.identity.infrastructure.token;

import com.fooddelivery.identity.infrastructure.persistence.SigningKeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.InitializingBean;

/**
 * Publishes the public half of the signing key before any token is issued. Ephemeral keys expire
 * from the JWKS after a day, so restarts on a developer machine do not pile up keys.
 */
public class SigningKeyRegistrar implements InitializingBean {

  static final Duration EPHEMERAL_KEY_LIFETIME = Duration.ofDays(1);

  private final SigningKey key;
  private final SigningKeyStore store;
  private final Clock clock;

  public SigningKeyRegistrar(SigningKey key, SigningKeyStore store, Clock clock) {
    this.key = key;
    this.store = store;
    this.clock = clock;
  }

  @Override
  public void afterPropertiesSet() {
    Instant retireAfter = key.ephemeral() ? clock.instant().plus(EPHEMERAL_KEY_LIFETIME) : null;
    store.registerActive(key.kid(), key.publicJwkJson(), retireAfter);
  }
}
