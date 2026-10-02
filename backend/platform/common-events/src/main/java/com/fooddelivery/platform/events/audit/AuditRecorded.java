package com.fooddelivery.platform.events.audit;

import com.fooddelivery.platform.events.EventEnvelope.Actor;
import com.fooddelivery.platform.events.outbox.NewEvent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * An audit record of a sensitive operation (REQ-AUDIT-001 AC2; docs/08 §3 audit.events.v1). Publish
 * it through the outbox in the transaction of the change, so the change cannot commit without it
 * (AC4). Who and when travel in the envelope ({@code actor}, {@code occurredAt}, {@code
 * correlationId}). Values must already be masked: no secrets, full card or phone numbers.
 *
 * <pre>{@code
 * outbox.publish(
 *     AuditRecorded.of(roles, "ROLE_GRANTED", "User", userId, null, after, reason, ip)
 *         .toEvent(Actor.user(adminId)));
 * }</pre>
 *
 * @param actorRoles the actor's roles when acting; empty for the system
 * @param oldValue the state before the change; null for a creation
 * @param newValue the state after the change; null for a deletion
 * @param ip the client address where the action came from an HTTP request
 */
public record AuditRecorded(
    List<String> actorRoles,
    String action,
    String entityType,
    UUID entityId,
    Object oldValue,
    Object newValue,
    String reason,
    String ip,
    String traceId) {

  public static final String TOPIC = "audit.events.v1";
  public static final String TYPE = "AuditRecorded";
  public static final int VERSION = 1;

  /** MDC key Micrometer Tracing fills for the current span. */
  static final String TRACE_ID_MDC_KEY = "traceId";

  public AuditRecorded {
    actorRoles = actorRoles == null ? List.of() : List.copyOf(actorRoles);
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(entityType, "entityType");
    Objects.requireNonNull(entityId, "entityId");
  }

  /** Takes the trace ID of the current request, if any. */
  public static AuditRecorded of(
      List<String> actorRoles,
      String action,
      String entityType,
      UUID entityId,
      Object oldValue,
      Object newValue,
      String reason,
      String ip) {
    return new AuditRecorded(
        actorRoles,
        action,
        entityType,
        entityId,
        oldValue,
        newValue,
        reason,
        ip,
        MDC.get(TRACE_ID_MDC_KEY));
  }

  /** Keyed by the entity, so the records of one entity stay in order (docs/08 §2). */
  public NewEvent toEvent(Actor actor) {
    return NewEvent.builder(TOPIC, TYPE, VERSION)
        .aggregate(entityType, entityId, null)
        .actor(actor)
        .payload(this)
        .build();
  }
}
