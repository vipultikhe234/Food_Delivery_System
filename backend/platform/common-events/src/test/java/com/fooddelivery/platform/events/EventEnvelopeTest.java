package com.fooddelivery.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fooddelivery.platform.events.consumer.IdempotentEventProcessor;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EventEnvelopeTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final IdempotentEventProcessor processor =
      new IdempotentEventProcessor(
          mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), JSON);

  @Test
  void serialisedFieldsMatchTheSchemaProperties() throws Exception {
    JsonNode schema = schema();
    List<String> schemaProperties = new ArrayList<>(schema.get("properties").propertyNames());
    List<String> serialised = new ArrayList<>(JSON.valueToTree(envelope()).propertyNames());

    assertThat(serialised).containsExactlyInAnyOrderElementsOf(schemaProperties);
  }

  @Test
  void everyFieldTheSchemaRequiresIsEnforcedAndNoOther() throws Exception {
    List<String> required = new ArrayList<>();
    schema().get("required").forEach(node -> required.add(node.asString()));

    for (String field : JSON.valueToTree(envelope()).propertyNames()) {
      ObjectNode json = JSON.valueToTree(envelope());
      json.remove(field);

      if (required.contains(field)) {
        assertThatThrownBy(() -> processor.parse(json.toString()))
            .as(field)
            .isInstanceOf(InvalidEventException.class)
            .hasMessageContaining(field);
      } else {
        assertThat(processor.parse(json.toString())).as(field).isNotNull();
      }
    }
  }

  @Test
  void roundTripsAndToleratesUnknownFields() {
    EventEnvelope envelope = envelope();
    ObjectNode json = JSON.valueToTree(envelope);
    json.put("addedInALaterMinorVersion", true);

    assertThat(processor.parse(json.toString())).isEqualTo(envelope);
  }

  @Test
  void malformedMessagesAreInvalidEvents() {
    assertThatThrownBy(() -> processor.parse("not json")).isInstanceOf(InvalidEventException.class);
    assertThatThrownBy(() -> processor.parse(null)).isInstanceOf(InvalidEventException.class);

    ObjectNode arrayPayload = JSON.valueToTree(envelope());
    arrayPayload.putArray("payload");
    assertThatThrownBy(() -> processor.parse(arrayPayload.toString()))
        .isInstanceOf(InvalidEventException.class);

    ObjectNode versionZero = JSON.valueToTree(envelope());
    versionZero.put("eventVersion", 0);
    assertThatThrownBy(() -> processor.parse(versionZero.toString()))
        .isInstanceOf(InvalidEventException.class);
  }

  static EventEnvelope envelope() {
    ObjectNode payload = JSON.createObjectNode().put("orderId", "o-1");
    return new EventEnvelope(
        UUID.randomUUID(),
        "OrderConfirmed",
        1,
        Instant.parse("2026-10-01T07:31:12.345Z"),
        "order-service",
        "Order",
        UUID.randomUUID(),
        4L,
        "c-1",
        UUID.randomUUID(),
        EventEnvelope.Actor.SYSTEM,
        payload);
  }

  private static JsonNode schema() throws Exception {
    try (InputStream in =
        EventEnvelopeTest.class.getResourceAsStream("/event-contracts/envelope.schema.json")) {
      assertThat(in).as("envelope schema on the classpath").isNotNull();
      return JSON.readTree(in);
    }
  }
}
