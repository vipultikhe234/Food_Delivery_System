package com.fooddelivery.identity.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokensTest {

  @Test
  void tokensAre256RandomBitsInUrlSafeBase64() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      String token = RefreshTokens.generate();
      assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
      assertThat(seen.add(token)).isTrue();
    }
  }

  @Test
  void theStoredHashIsSha256Hex() {
    // SHA-256("abc"), FIPS 180-2 test vector
    assertThat(RefreshTokens.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
