package com.fooddelivery.platform.persistence.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Time-ordered UUID version 7 (RFC 9562 §5.7) generated in the application (docs/06 §1.1).
 *
 * <p>Ids from one JVM are strictly increasing: within the same millisecond the 12-bit {@code
 * rand_a} field is used as a counter (RFC 9562 §6.2, method 1). Ordering by id therefore matches
 * creation order for rows written by one instance, which the outbox relies on.
 */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final UuidV7 SYSTEM = new UuidV7(Clock.systemUTC());

  private final Clock clock;
  private long lastMillis = -1;
  private int counter;

  UuidV7(Clock clock) {
    this.clock = clock;
  }

  public static UUID generate() {
    return SYSTEM.next();
  }

  synchronized UUID next() {
    long millis = clock.millis();
    if (millis > lastMillis) {
      lastMillis = millis;
      counter = RANDOM.nextInt(1 << 11);
    } else {
      counter++;
      if (counter > 0xFFF) {
        lastMillis++;
        counter = RANDOM.nextInt(1 << 11);
      }
    }
    long msb = (lastMillis & 0xFFFF_FFFF_FFFFL) << 16 | 0x7000L | counter;
    long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
    return new UUID(msb, lsb);
  }
}
