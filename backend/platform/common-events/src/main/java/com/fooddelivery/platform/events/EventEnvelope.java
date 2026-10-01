package com.fooddelivery.platform.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The envelope of every event and command (docs/08 §1, REQ-PLAT-005 AC1). The JSON Schema is {@code
 * event-contracts/envelope.schema.json}; a test keeps the two in step. Readers ignore unknown
 * fields, so adding an optional field stays compatible (docs/08 §7).
 *
 * @param aggregateVersion lets read models discard stale events; null when not versioned
 * @param causationId the event that caused this one; null for events caused by a user request
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
    UUID eventId,
    String eventType,
    Integer eventVersion,
    Instant occurredAt,
    String producer,
    String aggregateType,
    UUID aggregateId,
    Long aggregateVersion,
    String correlationId,
    UUID causationId,
    Actor actor,
    JsonNode payload) {

  /** Who caused the change ({@code actor_type} USER or SYSTEM, docs/06). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Actor(String type, String id) {

    public static final Actor SYSTEM = new Actor("SYSTEM", null);

    public static Actor user(UUID userId) {
      return new Actor("USER", userId.toString());
    }
  }

  /** Checks the required fields; a consumer sends an invalid envelope straight to the DLT. */
  public EventEnvelope validate() {
    List<String> missing = new ArrayList<>();
    require(eventId, "eventId", missing);
    require(eventType, "eventType", missing);
    require(eventVersion, "eventVersion", missing);
    require(occurredAt, "occurredAt", missing);
    require(producer, "producer", missing);
    require(aggregateType, "aggregateType", missing);
    require(aggregateId, "aggregateId", missing);
    require(correlationId, "correlationId", missing);
    require(actor, "actor", missing);
    require(payload, "payload", missing);
    if (actor != null && actor.type() == null) {
      missing.add("actor.type");
    }
    if (!missing.isEmpty()) {
      throw new InvalidEventException("envelope is missing " + String.join(", ", missing));
    }
    if (eventVersion < 1) {
      throw new InvalidEventException("eventVersion must be >= 1");
    }
    if (!payload.isObject()) {
      throw new InvalidEventException("payload must be a JSON object");
    }
    return this;
  }

  private static void require(Object value, String name, List<String> missing) {
    if (value == null) {
      missing.add(name);
    }
  }
}
