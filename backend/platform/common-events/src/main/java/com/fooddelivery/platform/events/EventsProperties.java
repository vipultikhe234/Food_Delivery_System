package com.fooddelivery.platform.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code fdp.events.*}: outbox relay (docs/08 §6) and consumer retry policy (docs/08 §5). */
@ConfigurationProperties("fdp.events")
public record EventsProperties(@DefaultValue Outbox outbox, @DefaultValue Consumer consumer) {

  /**
   * @param sendTimeout how long the relay waits for broker acknowledgements of one batch
   * @param maxBackoff the longest pause between relay attempts while Kafka is failing
   * @param retention how long published rows are kept before the nightly purge (docs/06 §6)
   */
  public record Outbox(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("200ms") Duration pollInterval,
      @DefaultValue("500") int batchSize,
      @DefaultValue("20s") Duration sendTimeout,
      @DefaultValue("30s") Duration maxBackoff,
      @DefaultValue("15s") Duration statsInterval,
      @DefaultValue("7d") Duration retention) {}

  /**
   * Blocking retries for order-sensitive consumers: 3 retries at 1 s, 5 s and 25 s, then the DLT.
   *
   * @param retention how long processed event ids are kept (docs/06 §6)
   */
  public record Consumer(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("1s") Duration initialInterval,
      @DefaultValue("5.0") double multiplier,
      @DefaultValue("3") int maxRetries,
      @DefaultValue("14d") Duration retention) {}
}
