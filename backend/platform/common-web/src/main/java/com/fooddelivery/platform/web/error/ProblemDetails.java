package com.fooddelivery.platform.web.error;

import com.fooddelivery.platform.observability.correlation.CorrelationId;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Builds error bodies in the platform format: RFC 9457 Problem Details extended with {@code code},
 * {@code correlationId}, {@code timestamp} and, for validation failures, {@code errors}
 * (docs/07-api-design.md §3, REQ-PLAT-004 v2).
 */
public class ProblemDetails {

  public static final String TYPE_PREFIX = "https://docs.fooddelivery.example/errors/";
  static final String GENERIC_SERVER_DETAIL = "An unexpected error occurred";

  private final Clock clock;

  public ProblemDetails(Clock clock) {
    this.clock = clock;
  }

  public ResponseEntity<ProblemDetail> response(
      ErrorCode code,
      HttpStatusCode status,
      String detail,
      List<FieldViolation> errors,
      String path,
      HttpServletResponse response) {
    ProblemDetail body = ProblemDetail.forStatus(status);
    body.setType(URI.create(TYPE_PREFIX + code.code()));
    body.setTitle(code.title());
    body.setDetail(status.is5xxServerError() ? GENERIC_SERVER_DETAIL : detail);
    body.setInstance(instance(path));
    body.setProperty("code", code.code());
    body.setProperty("correlationId", correlationId(response));
    body.setProperty("timestamp", Instant.now(clock).toString());
    if (!errors.isEmpty()) {
      body.setProperty("errors", errors);
    }
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }

  private static URI instance(String path) {
    if (path == null) {
      return null;
    }
    try {
      return URI.create(path);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String correlationId(HttpServletResponse response) {
    String fromMdc = MDC.get(CorrelationId.MDC_KEY);
    return fromMdc != null ? fromMdc : response.getHeader(CorrelationId.HEADER);
  }
}
