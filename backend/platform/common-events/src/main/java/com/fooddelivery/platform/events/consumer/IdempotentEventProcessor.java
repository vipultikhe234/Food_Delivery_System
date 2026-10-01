package com.fooddelivery.platform.events.consumer;

import com.fooddelivery.platform.events.EventEnvelope;
import com.fooddelivery.platform.events.EventScope;
import com.fooddelivery.platform.events.InvalidEventException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs a handler at most once per event and consumer group (docs/08 §4, REQ-PLAT-005 AC2).
 *
 * <pre>{@code
 * @KafkaListener(topics = "order.events.v1", groupId = "notification.dispatch")
 * void on(ConsumerRecord<String, String> record) {
 *   events.process("notification.dispatch", record, envelope -> switch (envelope.eventType()) { ... });
 * }
 * }</pre>
 *
 * <p>The processed-event marker and the handler's changes (including outbox writes) commit in one
 * transaction. If the handler throws, nothing is marked and the error handler retries or
 * dead-letters the record.
 */
public class IdempotentEventProcessor {

  private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);
  private static final Pattern CONSUMER = Pattern.compile("[a-z][a-z0-9.-]{0,127}");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final JsonMapper json;

  public IdempotentEventProcessor(
      JdbcTemplate jdbc, PlatformTransactionManager transactionManager, JsonMapper json) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
    this.json = json;
  }

  /** Returns false when the event was already processed by this consumer. */
  public boolean process(
      String consumer, ConsumerRecord<String, String> record, Consumer<EventEnvelope> handler) {
    if (!CONSUMER.matcher(consumer).matches()) {
      throw new IllegalArgumentException("invalid consumer name: " + consumer);
    }
    EventEnvelope envelope = parse(record.value());
    try (EventScope scope = EventScope.open(envelope)) {
      Boolean processed =
          transactions.execute(
              status -> {
                int inserted =
                    jdbc.update(
                        "INSERT INTO processed_events (event_id, consumer) VALUES (?, ?)"
                            + " ON CONFLICT DO NOTHING",
                        envelope.eventId(),
                        consumer);
                if (inserted == 0) {
                  log.debug("Skipping duplicate event {} for {}", envelope.eventId(), consumer);
                  return false;
                }
                handler.accept(envelope);
                return true;
              });
      return Boolean.TRUE.equals(processed);
    }
  }

  public EventEnvelope parse(String value) {
    if (value == null) {
      throw new InvalidEventException("record has no value");
    }
    try {
      return json.readValue(value, EventEnvelope.class).validate();
    } catch (JacksonException e) {
      throw new InvalidEventException("record is not a valid event envelope", e);
    }
  }
}
