package com.fooddelivery.platform.events.it;

import static com.fooddelivery.platform.events.it.EventsTestApplication.CONSUMER;
import static com.fooddelivery.platform.events.it.EventsTestApplication.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.awaitility.Awaitility.await;

import com.fooddelivery.platform.events.EventEnvelope;
import com.fooddelivery.platform.events.EventHeaders;
import com.fooddelivery.platform.events.InvalidEventException;
import com.fooddelivery.platform.testsupport.Containers;
import com.fooddelivery.platform.testsupport.RequiresDocker;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** REQ-PLAT-005 AC1–AC4 and REQ-PLAT-006 AC1 against PostgreSQL and Kafka (docs/08 §4–§6). */
@RequiresDocker
@Testcontainers
@SpringBootTest(
    properties = {
      "spring.application.name=events-test",
      "fdp.events.outbox.enabled=true",
      "fdp.events.outbox.poll-interval=100ms",
      "fdp.events.outbox.max-backoff=2s",
      "fdp.events.consumer.enabled=true",
      "fdp.events.consumer.initial-interval=100ms",
      "fdp.events.consumer.multiplier=2"
    })
class EventsIntegrationTest {

  private static final Duration WAIT = Duration.ofSeconds(60);

  @Container static final PostgreSQLContainer DB = Containers.postgres();
  @Container static final KafkaContainer KAFKA = Containers.kafka();

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DB::getJdbcUrl);
    registry.add("spring.datasource.username", DB::getUsername);
    registry.add("spring.datasource.password", DB::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Autowired EventsTestApplication.Notes notes;
  @Autowired EventsTestApplication.Received received;
  @Autowired JdbcTemplate jdbc;
  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired MeterRegistry meters;

  @Test
  void anEventIsPublishedWithItsEnvelopeKeyAndHeaders() {
    UUID aggregate = UUID.randomUUID();

    UUID eventId = notes.add(aggregate, Map.of("text", "hello"));

    EventEnvelope envelope = awaitHandled(aggregate, 1).getFirst();
    assertThat(envelope.eventId()).isEqualTo(eventId);
    assertThat(envelope.eventId().version()).isEqualTo(7);
    assertThat(envelope.eventType()).isEqualTo("NoteAdded");
    assertThat(envelope.eventVersion()).isEqualTo(1);
    assertThat(envelope.producer()).isEqualTo("events-test");
    assertThat(envelope.aggregateType()).isEqualTo("Note");
    assertThat(envelope.correlationId()).isNotBlank();
    assertThat(envelope.actor().type()).isEqualTo("SYSTEM");
    assertThat(envelope.payload().path("text").asString()).isEqualTo("hello");

    ConsumerRecord<String, String> record = recordFor(eventId);
    assertThat(record.key()).isEqualTo(aggregate.toString());
    assertThat(header(record, EventHeaders.EVENT_TYPE)).isEqualTo("NoteAdded");
    assertThat(header(record, EventHeaders.EVENT_VERSION)).isEqualTo("1");
    assertThat(header(record, EventHeaders.CORRELATION_ID)).isEqualTo(envelope.correlationId());
    assertThat(
            jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_events WHERE id = ?",
                Boolean.class,
                eventId))
        .isTrue();
  }

  @Test
  void aRolledBackTransactionLeavesNoEvent() {
    UUID aggregate = UUID.randomUUID();

    assertThatIllegalStateException().isThrownBy(() -> notes.addThenFail(aggregate));

    assertThat(count("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", aggregate))
        .isZero();
    assertThat(count("SELECT count(*) FROM notes WHERE aggregate_id = ?", aggregate)).isZero();
  }

  @Test
  void eventsForOneAggregateAreConsumedInProductionOrder() {
    UUID aggregate = UUID.randomUUID();

    IntStream.range(0, 20).forEach(seq -> notes.add(aggregate, Map.of("seq", seq)));

    List<Integer> order =
        awaitHandled(aggregate, 20).stream().map(e -> e.payload().path("seq").asInt()).toList();
    assertThat(order).isEqualTo(IntStream.range(0, 20).boxed().toList());
  }

  @Test
  void aRedeliveredEventHasNoAdditionalEffect() throws Exception {
    UUID aggregate = UUID.randomUUID();
    UUID eventId = notes.add(aggregate, Map.of("seq", 0));
    awaitHandled(aggregate, 1);
    String envelope =
        jdbc.queryForObject(
            "SELECT payload::text FROM outbox_events WHERE id = ?", String.class, eventId);

    kafka.send(TOPIC, aggregate.toString(), envelope).get();
    kafka.send(TOPIC, aggregate.toString(), envelope).get();
    notes.add(aggregate, Map.of("seq", 1));

    List<EventEnvelope> handled = awaitHandled(aggregate, 2);
    assertThat(handled).extracting(e -> e.payload().path("seq").asInt()).containsExactly(0, 1);
    assertThat(received.attemptsFor(eventId)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM processed_events WHERE event_id = ? AND consumer = '"
                    + CONSUMER
                    + "'",
                eventId))
        .isEqualTo(1);
  }

  @Test
  void aPoisonMessageGoesStraightToTheDeadLetterTopic() throws Exception {
    String key = UUID.randomUUID().toString();

    kafka.send(TOPIC, key, "this is not an event envelope").get();

    ConsumerRecord<String, String> dead = awaitDeadLetter(key);
    assertThat(dead.value()).isEqualTo("this is not an event envelope");
    assertThat(header(dead, EventHeaders.EXCEPTION_CLASS))
        .isEqualTo(InvalidEventException.class.getName());
    assertThat(header(dead, EventHeaders.ORIGINAL_TOPIC)).isEqualTo(TOPIC);
    assertThat(header(dead, EventHeaders.ORIGINAL_OFFSET)).isNotBlank();
    assertThat(header(dead, EventHeaders.FAILED_AT)).isNotBlank();
    assertThat(dead.headers().lastHeader("kafka_dlt-exception-stacktrace")).isNull();
    assertThat(dead.headers().lastHeader("kafka_dlt-exception-message")).isNull();
    assertThat(meters.counter("events.dead.lettered", "topic", TOPIC).count()).isPositive();
  }

  @Test
  void anInvalidTransitionIsNotRetried() {
    UUID aggregate = UUID.randomUUID();

    UUID eventId = notes.add(aggregate, Map.of("invalidTransition", true));

    awaitDeadLetter(aggregate.toString());
    assertThat(received.attemptsFor(eventId)).isEqualTo(1);
  }

  @Test
  void aFailingHandlerIsRetriedThenDeadLetteredWithASanitisedMessage() {
    UUID aggregate = UUID.randomUUID();

    UUID eventId = notes.add(aggregate, Map.of("fail", true));

    ConsumerRecord<String, String> dead = awaitDeadLetter(aggregate.toString());
    assertThat(received.attemptsFor(eventId)).isEqualTo(4);
    assertThat(header(dead, EventHeaders.EXCEPTION_CLASS))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(header(dead, EventHeaders.EXCEPTION_MESSAGE))
        .startsWith("handler failed for")
        .doesNotContain("customer@example.com")
        .doesNotContain("\n");
    assertThat(header(dead, EventHeaders.EVENT_TYPE)).isEqualTo("NoteAdded");
    assertThat(
            count(
                "SELECT count(*) FROM processed_events WHERE event_id = ? AND consumer = '"
                    + CONSUMER
                    + "'",
                eventId))
        .isZero();
  }

  @Test
  void stateChangesCommitWhileKafkaIsDownAndNoEventIsLost() {
    UUID aggregate = UUID.randomUUID();
    double failuresBefore = meters.counter("outbox.failures").count();
    var docker = DockerClientFactory.instance().client();

    docker.pauseContainerCmd(KAFKA.getContainerId()).exec();
    try {
      IntStream.range(0, 5).forEach(seq -> notes.add(aggregate, Map.of("seq", seq)));
      assertThat(count("SELECT count(*) FROM notes WHERE aggregate_id = ?", aggregate))
          .isEqualTo(5);
      await().atMost(WAIT).until(() -> meters.counter("outbox.failures").count() > failuresBefore);
      assertThat(
              count(
                  "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL",
                  aggregate))
          .isEqualTo(5);
    } finally {
      docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    List<Integer> order =
        awaitHandled(aggregate, 5).stream().map(e -> e.payload().path("seq").asInt()).toList();
    assertThat(order).containsExactly(0, 1, 2, 3, 4);
  }

  private List<EventEnvelope> awaitHandled(UUID aggregate, int count) {
    await().atMost(WAIT).until(() -> received.handledFor(aggregate).size() >= count);
    return received.handledFor(aggregate);
  }

  private ConsumerRecord<String, String> awaitDeadLetter(String key) {
    await()
        .atMost(WAIT)
        .until(() -> received.deadLetters.stream().anyMatch(r -> key.equals(r.key())));
    return received.deadLetters.stream().filter(r -> key.equals(r.key())).findFirst().orElseThrow();
  }

  private ConsumerRecord<String, String> recordFor(UUID eventId) {
    return received.records.stream()
        .filter(r -> r.value() != null && r.value().contains(eventId.toString()))
        .findFirst()
        .orElseThrow();
  }

  private long count(String sql, UUID id) {
    Long value = jdbc.queryForObject(sql, Long.class, id);
    return value == null ? 0 : value;
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
