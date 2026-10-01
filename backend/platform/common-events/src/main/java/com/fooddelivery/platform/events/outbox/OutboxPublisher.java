package com.fooddelivery.platform.events.outbox;

import com.fooddelivery.platform.events.EventContext;
import com.fooddelivery.platform.events.EventEnvelope;
import com.fooddelivery.platform.events.EventHeaders;
import com.fooddelivery.platform.observability.correlation.CorrelationId;
import com.fooddelivery.platform.persistence.id.UuidV7;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes events to {@code outbox_events} in the caller's transaction (ADR-005, REQ-PLAT-006 AC1):
 * the event is committed exactly when the state change is, and the relay publishes it later, even
 * if Kafka was down at the time.
 *
 * <p>Pending JPA changes are flushed first, so the aggregate row is locked before the outbox row is
 * created and rows for one aggregate are created in commit order.
 */
public class OutboxPublisher {

  private static final String INSERT =
      """
      INSERT INTO outbox_events
          (id, aggregate_type, aggregate_id, event_type, event_version, topic, partition_key,
           payload, headers, created_at)
      VALUES (:id, :aggregateType, :aggregateId, :eventType, :eventVersion, :topic, :partitionKey,
              CAST(:payload AS jsonb), CAST(:headers AS jsonb), clock_timestamp())
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final JsonMapper json;
  private final String producer;
  private final Clock clock;
  private final ObjectProvider<EntityManagerFactory> entityManagerFactory;
  private final ObjectProvider<Tracer> tracer;

  public OutboxPublisher(
      NamedParameterJdbcTemplate jdbc,
      JsonMapper json,
      String producer,
      Clock clock,
      ObjectProvider<EntityManagerFactory> entityManagerFactory,
      ObjectProvider<Tracer> tracer) {
    this.jdbc = jdbc;
    this.json = json;
    this.producer = producer;
    this.clock = clock;
    this.entityManagerFactory = entityManagerFactory;
    this.tracer = tracer;
  }

  /** Returns the eventId. Must be called inside the transaction that makes the state change. */
  public UUID publish(NewEvent event) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "OutboxPublisher.publish must run inside the business transaction");
    }
    flushPendingEntityChanges();

    Optional<EventEnvelope> cause = EventContext.current();
    EventEnvelope envelope =
        new EventEnvelope(
            UuidV7.generate(),
            event.eventType(),
            event.eventVersion(),
            Instant.now(clock),
            producer,
            event.aggregateType(),
            event.aggregateId(),
            event.aggregateVersion(),
            correlationId(cause),
            cause.map(EventEnvelope::eventId).orElse(null),
            event.actor(),
            json.valueToTree(event.payload()));

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(EventHeaders.EVENT_TYPE, envelope.eventType());
    headers.put(EventHeaders.EVENT_VERSION, String.valueOf(envelope.eventVersion()));
    headers.put(EventHeaders.CORRELATION_ID, envelope.correlationId());
    traceparent().ifPresent(value -> headers.put(EventHeaders.TRACEPARENT, value));

    jdbc.update(
        INSERT,
        new MapSqlParameterSource()
            .addValue("id", envelope.eventId())
            .addValue("aggregateType", envelope.aggregateType())
            .addValue("aggregateId", envelope.aggregateId())
            .addValue("eventType", envelope.eventType())
            .addValue("eventVersion", envelope.eventVersion())
            .addValue("topic", event.topic())
            .addValue("partitionKey", event.partitionKey())
            .addValue("payload", json.writeValueAsString(envelope))
            .addValue("headers", json.writeValueAsString(headers)));
    return envelope.eventId();
  }

  private void flushPendingEntityChanges() {
    EntityManagerFactory factory = entityManagerFactory.getIfAvailable();
    if (factory == null) {
      return;
    }
    EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
    if (entityManager != null && entityManager.isJoinedToTransaction()) {
      entityManager.flush();
    }
  }

  private static String correlationId(Optional<EventEnvelope> cause) {
    String fromRequest = MDC.get(CorrelationId.MDC_KEY);
    if (CorrelationId.isValid(fromRequest)) {
      return fromRequest;
    }
    return cause.map(EventEnvelope::correlationId).orElseGet(CorrelationId::newId);
  }

  private Optional<String> traceparent() {
    Tracer current = tracer.getIfAvailable();
    if (current == null || current.currentSpan() == null) {
      return Optional.empty();
    }
    TraceContext context = current.currentSpan().context();
    String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
    return Optional.of("00-" + context.traceId() + "-" + context.spanId() + "-" + flags);
  }
}
