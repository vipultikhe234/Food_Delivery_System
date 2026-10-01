package com.fooddelivery.platform.observability.correlation;

import java.util.UUID;
import java.util.regex.Pattern;

/** Correlation id conventions shared by the gateway and all services (docs/07-api-design.md §2). */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  private CorrelationId() {}

  /**
   * Returns the incoming id when it is well formed, otherwise a new one. Malformed values are
   * replaced rather than rejected, so that a bad header cannot inject content into logs.
   */
  public static String acceptOrCreate(String incoming) {
    return isValid(incoming) ? incoming : newId();
  }

  public static boolean isValid(String value) {
    return value != null && VALID.matcher(value).matches();
  }

  public static String newId() {
    return UUID.randomUUID().toString();
  }
}
