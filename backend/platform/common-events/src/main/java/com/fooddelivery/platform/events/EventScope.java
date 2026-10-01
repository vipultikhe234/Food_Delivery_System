package com.fooddelivery.platform.events;

import com.fooddelivery.platform.observability.correlation.CorrelationId;
import org.slf4j.MDC;

/** Puts a consumed event into {@link EventContext} and its correlation id into the log MDC. */
public final class EventScope implements AutoCloseable {

  private final String previousCorrelationId;

  private EventScope(EventEnvelope envelope) {
    this.previousCorrelationId = MDC.get(CorrelationId.MDC_KEY);
    EventContext.set(envelope);
    MDC.put(CorrelationId.MDC_KEY, CorrelationId.acceptOrCreate(envelope.correlationId()));
  }

  public static EventScope open(EventEnvelope envelope) {
    return new EventScope(envelope);
  }

  @Override
  public void close() {
    EventContext.clear();
    if (previousCorrelationId == null) {
      MDC.remove(CorrelationId.MDC_KEY);
    } else {
      MDC.put(CorrelationId.MDC_KEY, previousCorrelationId);
    }
  }
}
