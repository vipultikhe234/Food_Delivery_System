package com.fooddelivery.platform.events.outbox;

import com.fooddelivery.platform.events.EventEnvelope.Actor;
import java.util.Objects;
import java.util.UUID;

/**
 * An event to write to the outbox.
 *
 * <pre>{@code
 * outbox.publish(
 *     NewEvent.builder("order.events.v1", "OrderCreated", 1)
 *         .aggregate("Order", order.getId(), order.getVersion())
 *         .actor(Actor.user(customerId))
 *         .payload(new OrderCreated(...))
 *         .build());
 * }</pre>
 *
 * @param partitionKey the Kafka message key; defaults to the aggregate id. Topics keyed by another
 *     id (payment events by orderId, docs/08 §2) set it explicitly.
 */
public record NewEvent(
    String topic,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    Long aggregateVersion,
    String partitionKey,
    Actor actor,
    Object payload) {

  public NewEvent {
    Objects.requireNonNull(topic, "topic");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(aggregateType, "aggregateType");
    Objects.requireNonNull(aggregateId, "aggregateId");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(payload, "payload");
    if (eventVersion < 1) {
      throw new IllegalArgumentException("eventVersion must be >= 1");
    }
    if (partitionKey == null) {
      partitionKey = aggregateId.toString();
    }
  }

  public static Builder builder(String topic, String eventType, int eventVersion) {
    return new Builder(topic, eventType, eventVersion);
  }

  public static final class Builder {
    private final String topic;
    private final String eventType;
    private final int eventVersion;
    private String aggregateType;
    private UUID aggregateId;
    private Long aggregateVersion;
    private String partitionKey;
    private Actor actor = Actor.SYSTEM;
    private Object payload;

    private Builder(String topic, String eventType, int eventVersion) {
      this.topic = topic;
      this.eventType = eventType;
      this.eventVersion = eventVersion;
    }

    public Builder aggregate(String type, UUID id, Long version) {
      this.aggregateType = type;
      this.aggregateId = id;
      this.aggregateVersion = version;
      return this;
    }

    public Builder partitionKey(String partitionKey) {
      this.partitionKey = partitionKey;
      return this;
    }

    public Builder actor(Actor actor) {
      this.actor = actor;
      return this;
    }

    public Builder payload(Object payload) {
      this.payload = payload;
      return this;
    }

    public NewEvent build() {
      return new NewEvent(
          topic,
          eventType,
          eventVersion,
          aggregateType,
          aggregateId,
          aggregateVersion,
          partitionKey,
          actor,
          payload);
    }
  }
}
