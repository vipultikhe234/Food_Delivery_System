package com.fooddelivery.platform.web.error;

import org.springframework.http.HttpStatus;

/** Codes shared by all services (docs/07-api-design.md §3.1 and §3.2, "Common"). */
public enum CommonErrorCode implements ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid request"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
  IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "Request in progress"),
  IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused"),
  UPGRADE_REQUIRED(HttpStatus.UPGRADE_REQUIRED, "Upgrade required"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
  DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable");

  private final HttpStatus status;
  private final String title;

  CommonErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  @Override
  public String code() {
    return name();
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String title() {
    return title;
  }

  /** The code used when a framework error carries only an HTTP status. */
  public static CommonErrorCode forStatus(int status) {
    return switch (status) {
      case 401 -> UNAUTHENTICATED;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 409 -> CONCURRENT_MODIFICATION;
      case 426 -> UPGRADE_REQUIRED;
      case 429 -> RATE_LIMITED;
      case 502, 503 -> SERVICE_UNAVAILABLE;
      case 504 -> DEPENDENCY_UNAVAILABLE;
      default -> status >= 500 ? INTERNAL_ERROR : VALIDATION_FAILED;
    };
  }
}
