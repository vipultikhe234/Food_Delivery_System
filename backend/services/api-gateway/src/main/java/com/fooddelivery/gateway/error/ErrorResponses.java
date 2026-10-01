package com.fooddelivery.gateway.error;

import com.fooddelivery.platform.observability.correlation.CorrelationId;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.json.JsonWriter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes gateway-generated errors in the platform's standard error format (docs/07-api-design.md
 * §3). Messages are generic: no exception text, class names or hosts reach the client.
 */
public final class ErrorResponses {

  static final String TYPE_PREFIX = "https://docs.fooddelivery.example/errors/";
  private static final JsonWriter<Map<String, Object>> JSON = JsonWriter.standard();

  private ErrorResponses() {}

  public static Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status) {
    return write(exchange, status, Clock.systemUTC());
  }

  static Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, Clock clock) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }
    ErrorKind kind = ErrorKind.of(status);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", TYPE_PREFIX + kind.code());
    body.put("title", kind.title());
    body.put("status", status.value());
    body.put("code", kind.code());
    body.put("detail", kind.detail());
    body.put("instance", exchange.getRequest().getPath().value());
    body.put("correlationId", response.getHeaders().getFirst(CorrelationId.HEADER));
    body.put("timestamp", Instant.now(clock).toString());

    byte[] bytes = JSON.writeToString(body).getBytes(StandardCharsets.UTF_8);
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    response.getHeaders().setContentLength(bytes.length);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  enum ErrorKind {
    UNAUTHENTICATED("Unauthenticated", "A valid access token is required."),
    FORBIDDEN("Forbidden", "You do not have access to this resource."),
    NOT_FOUND("Not found", "The requested resource was not found."),
    VALIDATION_FAILED("Invalid request", "The request could not be processed."),
    RATE_LIMITED("Too many requests", "Too many requests. Try again later."),
    SERVICE_UNAVAILABLE("Service unavailable", "The service is temporarily unavailable."),
    DEPENDENCY_UNAVAILABLE("Dependency unavailable", "The service did not respond in time."),
    INTERNAL_ERROR("Internal error", "An unexpected error occurred");

    private final String title;
    private final String detail;

    ErrorKind(String title, String detail) {
      this.title = title;
      this.detail = detail;
    }

    String code() {
      return name();
    }

    String title() {
      return title;
    }

    String detail() {
      return detail;
    }

    static ErrorKind of(HttpStatusCode status) {
      HttpStatus resolved = HttpStatus.resolve(status.value());
      if (resolved == null) {
        return status.is5xxServerError() ? INTERNAL_ERROR : VALIDATION_FAILED;
      }
      return switch (resolved) {
        case UNAUTHORIZED -> UNAUTHENTICATED;
        case FORBIDDEN -> FORBIDDEN;
        case NOT_FOUND -> NOT_FOUND;
        case TOO_MANY_REQUESTS -> RATE_LIMITED;
        case SERVICE_UNAVAILABLE, BAD_GATEWAY -> SERVICE_UNAVAILABLE;
        case GATEWAY_TIMEOUT -> DEPENDENCY_UNAVAILABLE;
        default -> resolved.is5xxServerError() ? INTERNAL_ERROR : VALIDATION_FAILED;
      };
    }
  }
}
