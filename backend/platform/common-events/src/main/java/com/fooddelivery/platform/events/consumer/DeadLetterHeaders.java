package com.fooddelivery.platform.events.consumer;

import com.fooddelivery.platform.events.EventHeaders;
import com.fooddelivery.platform.observability.logging.PiiMasker;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

/**
 * Headers added to a dead-lettered record (docs/08 §5). The original headers are kept; the
 * exception message is masked, flattened to one line and truncated, and no stack trace is added.
 */
public class DeadLetterHeaders implements BiFunction<ConsumerRecord<?, ?>, Exception, Headers> {

  static final int MAX_MESSAGE_LENGTH = 500;

  private final Clock clock;

  public DeadLetterHeaders(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Headers apply(ConsumerRecord<?, ?> record, Exception exception) {
    Throwable cause = rootListenerCause(exception);
    RecordHeaders headers = new RecordHeaders();
    add(headers, EventHeaders.EXCEPTION_CLASS, cause.getClass().getName());
    add(headers, EventHeaders.EXCEPTION_MESSAGE, sanitise(cause.getMessage()));
    add(headers, EventHeaders.ORIGINAL_TOPIC, record.topic());
    add(headers, EventHeaders.ORIGINAL_OFFSET, String.valueOf(record.offset()));
    add(headers, EventHeaders.FAILED_AT, Instant.now(clock).toString());
    return headers;
  }

  static String sanitise(String message) {
    if (message == null) {
      return "";
    }
    String oneLine = PiiMasker.mask(message).replaceAll("\\p{Cntrl}+", " ").strip();
    return oneLine.length() <= MAX_MESSAGE_LENGTH
        ? oneLine
        : oneLine.substring(0, MAX_MESSAGE_LENGTH);
  }

  private static Throwable rootListenerCause(Exception exception) {
    Throwable cause = exception;
    while (cause instanceof ListenerExecutionFailedException && cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private static void add(RecordHeaders headers, String name, String value) {
    headers.add(name, value.getBytes(StandardCharsets.UTF_8));
  }
}
