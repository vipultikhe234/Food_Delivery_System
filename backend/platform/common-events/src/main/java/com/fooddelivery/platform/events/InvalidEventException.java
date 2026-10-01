package com.fooddelivery.platform.events;

/**
 * A message that can never be processed (malformed envelope, unknown event type, invalid state
 * transition). It goes to the DLT without retries (docs/08 §5).
 */
public class InvalidEventException extends RuntimeException {

  public InvalidEventException(String message) {
    super(message);
  }

  public InvalidEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
