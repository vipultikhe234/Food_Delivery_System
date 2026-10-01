package com.fooddelivery.platform.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.platform.events.EventHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

class DeadLetterHeadersTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
  private final DeadLetterHeaders headers = new DeadLetterHeaders(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void describesTheFailureAndTheOriginalPosition() {
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>("order.events.v1", 2, 41L, "key", "value");
    Exception failure =
        new ListenerExecutionFailedException(
            "listener failed", new IllegalStateException("no such order"));

    Headers result = headers.apply(record, failure);

    assertThat(value(result, EventHeaders.EXCEPTION_CLASS))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(value(result, EventHeaders.EXCEPTION_MESSAGE)).isEqualTo("no such order");
    assertThat(value(result, EventHeaders.ORIGINAL_TOPIC)).isEqualTo("order.events.v1");
    assertThat(value(result, EventHeaders.ORIGINAL_OFFSET)).isEqualTo("41");
    assertThat(value(result, EventHeaders.FAILED_AT)).isEqualTo(NOW.toString());
  }

  @Test
  void theMessageIsMaskedFlattenedAndTruncated() {
    String sanitised =
        DeadLetterHeaders.sanitise(
            "lookup failed for customer@example.com\nphone 9876543210\r\n" + "x".repeat(600));

    assertThat(sanitised)
        .doesNotContain("customer@example.com")
        .doesNotContain("9876543210")
        .doesNotContain("\n")
        .doesNotContain("\r")
        .hasSizeLessThanOrEqualTo(DeadLetterHeaders.MAX_MESSAGE_LENGTH);
    assertThat(DeadLetterHeaders.sanitise(null)).isEmpty();
  }

  private static String value(Headers headers, String name) {
    return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
  }
}
