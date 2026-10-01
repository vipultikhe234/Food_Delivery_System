package com.fooddelivery.platform.events;

/** Kafka header names (docs/08 §1 and §5). */
public final class EventHeaders {

  public static final String EVENT_TYPE = "eventType";
  public static final String EVENT_VERSION = "eventVersion";
  public static final String CORRELATION_ID = "correlationId";
  public static final String TRACEPARENT = "traceparent";

  public static final String EXCEPTION_CLASS = "x-exception-class";
  public static final String EXCEPTION_MESSAGE = "x-exception-message";
  public static final String ORIGINAL_TOPIC = "x-original-topic";
  public static final String ORIGINAL_OFFSET = "x-original-offset";
  public static final String FAILED_AT = "x-failed-at";

  private EventHeaders() {}
}
