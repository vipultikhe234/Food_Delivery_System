package com.fooddelivery.platform.events.outbox;

import com.fooddelivery.platform.events.EventsProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes outbox rows to Kafka (docs/08 §6).
 *
 * <p>One relay is active per service: each run takes a transaction-scoped advisory lock and other
 * instances skip the run. Rows are sent in creation order and the run stops at the first failure,
 * so a later event for an aggregate is never published before an earlier one. Rows sent after the
 * failure may reach Kafka without being marked; they are sent again later and consumers drop the
 * duplicates (REQ-PLAT-005 AC2).
 */
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  /** Advisory lock key: ASCII "fdpoutbx". One lock per service database. */
  static final long LOCK_KEY = 0x6664706f75746278L;

  private static final String SELECT =
      """
      SELECT id, topic, partition_key, payload::text AS payload, headers::text AS headers, created_at
        FROM outbox_events
       WHERE published_at IS NULL
       ORDER BY created_at, id
       LIMIT :limit
       FOR UPDATE SKIP LOCKED
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final KafkaTemplate<String, String> kafka;
  private final JsonMapper json;
  private final EventsProperties.Outbox properties;
  private final Clock clock;
  private final Counter failures;
  private final Timer latency;
  private final AtomicLong pending = new AtomicLong();
  private final AtomicLong oldestAgeSeconds = new AtomicLong();

  private int consecutiveFailures;
  private Instant pausedUntil = Instant.MIN;

  public OutboxRelay(
      NamedParameterJdbcTemplate jdbc,
      PlatformTransactionManager transactionManager,
      KafkaTemplate<String, String> kafka,
      JsonMapper json,
      EventsProperties.Outbox properties,
      MeterRegistry meters,
      Clock clock) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
    this.kafka = kafka;
    this.json = json;
    this.properties = properties;
    this.clock = clock;
    this.failures =
        Counter.builder("outbox.failures")
            .description("Outbox rows that failed to publish")
            .register(meters);
    this.latency =
        Timer.builder("outbox.publish.latency")
            .description("Time from outbox insert to broker acknowledgement")
            .publishPercentileHistogram()
            .register(meters);
    Gauge.builder("outbox.pending.count", pending, AtomicLong::get)
        .description("Unpublished outbox rows")
        .register(meters);
    Gauge.builder("outbox.oldest.unpublished.age", oldestAgeSeconds, AtomicLong::get)
        .description("Age of the oldest unpublished outbox row")
        .baseUnit("seconds")
        .register(meters);
  }

  @Scheduled(fixedDelayString = "${fdp.events.outbox.poll-interval:200ms}")
  public void poll() {
    if (Instant.now(clock).isBefore(pausedUntil)) {
      return;
    }
    try {
      relayBatch();
    } catch (RuntimeException e) {
      log.warn("Outbox relay run failed", e);
      backOff();
    }
  }

  /** Publishes one batch; returns the number of rows marked published. */
  public synchronized int relayBatch() {
    Integer published = transactions.execute(status -> relayLocked());
    return published == null ? 0 : published;
  }

  private int relayLocked() {
    Boolean locked =
        jdbc.getJdbcTemplate()
            .queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
    if (!Boolean.TRUE.equals(locked)) {
      return 0;
    }
    List<Map<String, Object>> rows =
        jdbc.queryForList(SELECT, Map.of("limit", properties.batchSize()));
    if (rows.isEmpty()) {
      consecutiveFailures = 0;
      return 0;
    }

    List<CompletableFuture<SendResult<String, String>>> sends = new ArrayList<>();
    Failure failure = null;
    for (Map<String, Object> row : rows) {
      try {
        sends.add(kafka.send(record(row)));
      } catch (RuntimeException e) {
        failure = new Failure(row, e);
        break;
      }
    }

    List<UUID> done = new ArrayList<>();
    for (int i = 0; i < sends.size(); i++) {
      Map<String, Object> row = rows.get(i);
      try {
        sends.get(i).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        done.add((UUID) row.get("id"));
        Instant created = ((Timestamp) row.get("created_at")).toInstant();
        latency.record(Duration.between(created, Instant.now(clock)));
      } catch (ExecutionException | TimeoutException e) {
        failure = new Failure(row, e instanceof ExecutionException ? e.getCause() : e);
        break;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        failure = new Failure(row, e);
        break;
      }
    }

    if (!done.isEmpty()) {
      jdbc.update(
          "UPDATE outbox_events SET published_at = now() WHERE id IN (:ids)", Map.of("ids", done));
    }
    if (failure != null) {
      recordFailure(failure);
    } else {
      consecutiveFailures = 0;
    }
    return done.size();
  }

  private ProducerRecord<String, String> record(Map<String, Object> row) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(
            (String) row.get("topic"),
            null,
            (String) row.get("partition_key"),
            (String) row.get("payload"));
    json.readTree((String) row.get("headers"))
        .properties()
        .forEach(
            header ->
                record
                    .headers()
                    .add(
                        new RecordHeader(
                            header.getKey(),
                            header.getValue().asString().getBytes(StandardCharsets.UTF_8))));
    return record;
  }

  private void recordFailure(Failure failure) {
    failures.increment();
    String message = String.valueOf(failure.cause());
    jdbc.update(
        "UPDATE outbox_events SET attempts = attempts + 1, last_error = :error WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("id", failure.row().get("id"))
            .addValue("error", message.length() <= 1000 ? message : message.substring(0, 1000)));
    log.warn("Outbox publish failed for event {}: {}", failure.row().get("id"), message);
    backOff();
  }

  private void backOff() {
    consecutiveFailures = Math.min(consecutiveFailures + 1, 16);
    long delay =
        Math.min(
            properties.maxBackoff().toMillis(),
            properties.pollInterval().toMillis() * (1L << consecutiveFailures));
    pausedUntil = Instant.now(clock).plusMillis(delay);
  }

  @Scheduled(
      fixedDelayString = "${fdp.events.outbox.stats-interval:15s}",
      initialDelayString = "${fdp.events.outbox.stats-interval:15s}")
  public void refreshStats() {
    Map<String, Object> stats =
        jdbc.getJdbcTemplate()
            .queryForMap(
                """
                SELECT count(*) AS pending,
                       coalesce(extract(epoch FROM now() - min(created_at)), 0) AS oldest
                  FROM outbox_events WHERE published_at IS NULL
                """);
    pending.set(((Number) stats.get("pending")).longValue());
    oldestAgeSeconds.set(((Number) stats.get("oldest")).longValue());
  }

  private record Failure(Map<String, Object> row, Throwable cause) {}
}
