package com.fooddelivery.platform.web.error;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thrown by application code to return a specific error code. The {@code detail} is sent to the
 * client, so it must be safe to show: no identifiers of other users, no internal names.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String detail;
  private final List<FieldViolation> errors;
  private final Map<String, String> headers;

  public ApiException(ErrorCode errorCode, String detail) {
    this(errorCode, detail, List.of(), Map.of(), null);
  }

  public ApiException(ErrorCode errorCode, String detail, Throwable cause) {
    this(errorCode, detail, List.of(), Map.of(), cause);
  }

  public ApiException(
      ErrorCode errorCode,
      String detail,
      List<FieldViolation> errors,
      Map<String, String> headers,
      Throwable cause) {
    super(errorCode.code() + ": " + detail, cause);
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    this.detail = Objects.requireNonNull(detail, "detail");
    this.errors = List.copyOf(errors);
    this.headers = Map.copyOf(headers);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public String detail() {
    return detail;
  }

  public List<FieldViolation> errors() {
    return errors;
  }

  /** Extra response headers, e.g. {@code Retry-After}. */
  public Map<String, String> headers() {
    return headers;
  }
}
