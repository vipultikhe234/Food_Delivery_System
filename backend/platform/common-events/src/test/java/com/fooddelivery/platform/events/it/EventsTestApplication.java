package com.fooddelivery.platform.events.it;

import com.fooddelivery.platform.events.EventEnvelope;
import com.fooddelivery.platform.events.EventTopics;
import com.fooddelivery.platform.events.InvalidEventException;
import com.fooddelivery.platform.events.consumer.IdempotentEventProcessor;
import com.fooddelivery.platform.events.outbox.NewEvent;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
class EventsTestApplication {

  static final String TOPIC = "test.events.v1";
  static final String CONSUMER = "test.consumer";

  @Bean
  NewTopic testEvents() {
    return EventTopics.topic(TOPIC, 3, (short) 1);
  }

  @Bean
  NewTopic testEventsDlt() {
    return EventTopics.deadLetter(TOPIC, 3, (short) 1);
  }

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  /** A service that changes state and writes its event in the same transaction. */
  @Component
  static class Notes {
    private final JdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    Notes(JdbcTemplate jdbc, OutboxPublisher outbox) {
      this.jdbc = jdbc;
      this.outbox = outbox;
    }

    @Transactional
    public UUID add(UUID aggregateId, Map<String, Object> payload) {
      jdbc.update(
          "INSERT INTO notes (id, aggregate_id) VALUES (?, ?)", UUID.randomUUID(), aggregateId);
      return outbox.publish(
          NewEvent.builder(TOPIC, "NoteAdded", 1)
              .aggregate("Note", aggregateId, null)
              .payload(payload)
              .build());
    }

    @Transactional
    public void addThenFail(UUID aggregateId) {
      add(aggregateId, Map.of("seq", 0));
      throw new IllegalStateException("business rule failed after the event was written");
    }
  }

  @Component
  static class Received {
    final List<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    final List<EventEnvelope> handled = new CopyOnWriteArrayList<>();
    final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();
    final List<ConsumerRecord<String, String>> deadLetters = new CopyOnWriteArrayList<>();

    List<EventEnvelope> handledFor(UUID aggregateId) {
      return handled.stream().filter(e -> e.aggregateId().equals(aggregateId)).toList();
    }

    int attemptsFor(UUID eventId) {
      return attempts.getOrDefault(eventId, new AtomicInteger()).get();
    }
  }

  @Component
  static class Listener {
    private final IdempotentEventProcessor events;
    private final Received received;

    Listener(IdempotentEventProcessor events, Received received) {
      this.events = events;
      this.received = received;
    }

    @KafkaListener(topics = TOPIC, groupId = CONSUMER)
    void on(ConsumerRecord<String, String> record) {
      received.records.add(record);
      events.process(
          CONSUMER,
          record,
          envelope -> {
            received
                .attempts
                .computeIfAbsent(envelope.eventId(), id -> new AtomicInteger())
                .incrementAndGet();
            if (envelope.payload().path("fail").asBoolean(false)) {
              throw new IllegalStateException(
                  "handler failed for customer@example.com\nwhile updating");
            }
            if (envelope.payload().path("invalidTransition").asBoolean(false)) {
              throw new InvalidEventException("DELIVERED -> CREATED is not allowed");
            }
            received.handled.add(envelope);
          });
    }

    @KafkaListener(topics = TOPIC + EventTopics.DLT_SUFFIX, groupId = "test.dlt-reader")
    void onDeadLetter(ConsumerRecord<String, String> record) {
      received.deadLetters.add(record);
    }
  }
}
