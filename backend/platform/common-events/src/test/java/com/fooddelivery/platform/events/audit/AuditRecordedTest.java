package com.fooddelivery.platform.events.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.platform.events.EventEnvelope.Actor;
import com.fooddelivery.platform.events.outbox.NewEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuditRecordedTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final UUID entityId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void isAnOutboxEventKeyedByTheEntityOnTheAuditTopic() {
    NewEvent event =
        AuditRecorded.of(
                List.of("SUPER_ADMIN"),
                "ROLE_GRANTED",
                "User",
                entityId,
                null,
                Map.of("role", "ADMIN"),
                "on-call cover",
                "10.0.0.7")
            .toEvent(Actor.user(adminId));

    assertThat(event.topic()).isEqualTo("audit.events.v1");
    assertThat(event.eventType()).isEqualTo("AuditRecorded");
    assertThat(event.eventVersion()).isEqualTo(1);
    assertThat(event.aggregateType()).isEqualTo("User");
    assertThat(event.aggregateId()).isEqualTo(entityId);
    assertThat(event.partitionKey()).isEqualTo(entityId.toString());
    assertThat(event.actor()).isEqualTo(Actor.user(adminId));
  }

  @Test
  void takesTheTraceIdOfTheCurrentRequest() {
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

    AuditRecorded record =
        AuditRecorded.of(null, "ROLE_REVOKED", "User", entityId, Map.of(), null, "left", null);

    assertThat(record.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(record.actorRoles()).isEmpty();
  }

  @Test
  void serialisedFieldsMatchTheSchemaProperties() throws Exception {
    JsonNode schema;
    try (InputStream in =
        getClass()
            .getResourceAsStream("/event-contracts/audit.events.v1/AuditRecorded.v1.schema.json")) {
      assertThat(in).as("AuditRecorded schema on the classpath").isNotNull();
      schema = JSON.readTree(in);
    }
    AuditRecorded record =
        new AuditRecorded(
            List.of("ADMIN"), "USER_BLOCKED", "User", entityId, Map.of(), Map.of(), "r", "ip", "t");

    List<String> serialised = new ArrayList<>(JSON.valueToTree(record).propertyNames());
    List<String> required = new ArrayList<>();
    schema.get("required").forEach(node -> required.add(node.asString()));

    assertThat(serialised)
        .containsExactlyInAnyOrderElementsOf(schema.get("properties").propertyNames());
    assertThat(serialised).containsAll(required);
  }
}
