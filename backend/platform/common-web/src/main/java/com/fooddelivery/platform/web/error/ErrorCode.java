package com.fooddelivery.platform.web.error;

import org.springframework.http.HttpStatus;

/**
 * A stable, machine-readable error code (docs/07-api-design.md §3.2). Each domain owns an enum that
 * implements this interface; clients switch on {@link #code()}, never on the detail text.
 */
public interface ErrorCode {

  /** The code exactly as sent to clients, e.g. {@code INVALID_ORDER_TRANSITION}. */
  String code();

  HttpStatus status();

  /** Short human-readable summary, the same for every occurrence of the code. */
  String title();
}
