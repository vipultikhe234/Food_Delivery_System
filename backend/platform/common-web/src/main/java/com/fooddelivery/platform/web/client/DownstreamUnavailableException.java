package com.fooddelivery.platform.web.client;

import com.fooddelivery.platform.web.error.ApiException;
import com.fooddelivery.platform.web.error.CommonErrorCode;

/**
 * A downstream call failed after the resilience policy gave up: timeout, connection failure, 5xx,
 * open circuit or full bulkhead. Call sites with a fallback catch it; otherwise the caller gets 503
 * {@code DEPENDENCY_UNAVAILABLE}. The client name and reason appear only in logs.
 */
public class DownstreamUnavailableException extends ApiException {

  private final String client;
  private final String reason;

  public DownstreamUnavailableException(String client, String reason, Throwable cause) {
    super(
        CommonErrorCode.DEPENDENCY_UNAVAILABLE,
        "A required service is temporarily unavailable. Try again later.",
        cause);
    this.client = client;
    this.reason = reason;
  }

  public String client() {
    return client;
  }

  public String reason() {
    return reason;
  }

  @Override
  public String getMessage() {
    return "Downstream " + client + " unavailable: " + reason;
  }
}
