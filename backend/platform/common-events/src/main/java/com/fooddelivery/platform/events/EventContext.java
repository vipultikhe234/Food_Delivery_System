package com.fooddelivery.platform.events;

import java.util.Optional;

/**
 * The event being consumed on this thread. Events published while handling it get its eventId as
 * {@code causationId} and keep its {@code correlationId} (docs/08 §1).
 */
public final class EventContext {

  private static final ThreadLocal<EventEnvelope> CURRENT = new ThreadLocal<>();

  private EventContext() {}

  public static Optional<EventEnvelope> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  static void set(EventEnvelope envelope) {
    CURRENT.set(envelope);
  }

  static void clear() {
    CURRENT.remove();
  }
}
