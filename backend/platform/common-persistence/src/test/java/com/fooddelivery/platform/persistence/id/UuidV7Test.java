package com.fooddelivery.platform.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void hasVersion7AndTheRfcVariant() {
    UUID id = UuidV7.generate();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void carriesTheCreationTimeInTheFirst48Bits() {
    Instant now = Instant.parse("2026-10-01T07:30:00.123Z");
    UUID id = new UuidV7(Clock.fixed(now, ZoneOffset.UTC)).next();

    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
  }

  @Test
  void idsAreStrictlyIncreasingEvenWithinOneMillisecond() {
    UuidV7 generator =
        new UuidV7(Clock.fixed(Instant.parse("2026-10-01T07:30:00Z"), ZoneOffset.UTC));
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 10_000; i++) {
      ids.add(generator.next());
    }

    for (int i = 1; i < ids.size(); i++) {
      assertThat(ids.get(i).toString()).isGreaterThan(ids.get(i - 1).toString());
    }
    assertThat(new HashSet<>(ids)).hasSize(ids.size());
  }
}
