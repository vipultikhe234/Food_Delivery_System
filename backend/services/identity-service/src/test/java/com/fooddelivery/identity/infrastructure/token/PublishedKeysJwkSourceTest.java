package com.fooddelivery.identity.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fooddelivery.identity.infrastructure.persistence.SigningKeyStore;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublishedKeysJwkSourceTest {

  private final SigningKeyStore store = mock(SigningKeyStore.class);
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T08:00:00Z"));
  private final PublishedKeysJwkSource source = new PublishedKeysJwkSource(store, clock);

  @Test
  void servesThePublishedKeysFromACache() throws Exception {
    RSAKey k1 = key("k1");
    when(store.publishedKeys()).thenReturn(List.of(k1.toJSONString()));

    assertThat(source.get(select("k1"), null)).extracting("keyID").containsExactly("k1");
    clock.advance(Duration.ofSeconds(30));
    assertThat(source.get(select("k1"), null)).hasSize(1);

    verify(store, times(1)).publishedKeys();

    clock.advance(PublishedKeysJwkSource.MAX_AGE);
    source.get(select("k1"), null);
    verify(store, times(2)).publishedKeys();
  }

  @Test
  void aKeyRotatedInOnAnotherReplicaIsFoundWithoutWaitingForTheCache() throws Exception {
    RSAKey k1 = key("k1");
    RSAKey k2 = key("k2");
    when(store.publishedKeys())
        .thenReturn(List.of(k1.toJSONString()), List.of(k1.toJSONString(), k2.toJSONString()));
    source.get(select("k1"), null);

    assertThat(source.get(select("k2"), null)).as("within the reload interval").isEmpty();

    clock.advance(PublishedKeysJwkSource.MIN_RELOAD_INTERVAL.plusSeconds(1));
    assertThat(source.get(select("k2"), null)).extracting("keyID").containsExactly("k2");
  }

  @Test
  void anUnparseableKeyIsAKeySourceFailure() {
    when(store.publishedKeys()).thenReturn(List.of("{not a jwk"));

    assertThatThrownBy(() -> source.get(select("k1"), null)).isInstanceOf(KeySourceException.class);
  }

  private static JWKSelector select(String kid) {
    return new JWKSelector(new JWKMatcher.Builder().keyID(kid).build());
  }

  private static RSAKey key(String kid) throws Exception {
    return new RSAKeyGenerator(2048).keyID(kid).generate().toPublicJWK();
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
