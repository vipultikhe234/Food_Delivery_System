package com.fooddelivery.identity.infrastructure.token;

import com.fooddelivery.identity.infrastructure.persistence.SigningKeyStore;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The keys identity-service validates its own access tokens with: the same set the JWKS endpoint
 * publishes, so tokens signed before a key rotation stay valid on every replica. The set is cached
 * and reloaded early when a token names an unknown key.
 */
public class PublishedKeysJwkSource implements JWKSource<SecurityContext> {

  static final Duration MAX_AGE = Duration.ofSeconds(60);
  static final Duration MIN_RELOAD_INTERVAL = Duration.ofSeconds(5);

  private record Snapshot(JWKSet keys, Instant loadedAt) {}

  private final SigningKeyStore store;
  private final Clock clock;
  private volatile Snapshot snapshot;

  public PublishedKeysJwkSource(SigningKeyStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Override
  public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
    Instant now = clock.instant();
    Snapshot current = snapshot;
    if (current == null || current.loadedAt().plus(MAX_AGE).isBefore(now)) {
      current = reload(now);
    }
    List<JWK> matches = selector.select(current.keys());
    if (matches.isEmpty() && current.loadedAt().plus(MIN_RELOAD_INTERVAL).isBefore(now)) {
      matches = selector.select(reload(now).keys());
    }
    return matches;
  }

  private synchronized Snapshot reload(Instant now) throws KeySourceException {
    List<JWK> keys = new ArrayList<>();
    for (String json : store.publishedKeys()) {
      try {
        keys.add(JWK.parse(json));
      } catch (ParseException e) {
        throw new KeySourceException("A published signing key could not be parsed", e);
      }
    }
    Snapshot loaded = new Snapshot(new JWKSet(keys), now);
    snapshot = loaded;
    return loaded;
  }
}
