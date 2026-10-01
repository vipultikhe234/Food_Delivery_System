package com.fooddelivery.gateway.error;

import com.fooddelivery.platform.observability.correlation.CorrelationId;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Turns every error raised inside the gateway (no route, no instance, timeout, unexpected failure)
 * into the standard error body. The cause is logged with the correlation id, never returned
 * (REQ-PLAT-004 AC2).
 */
@Component
@Order(-2)
class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    HttpStatusCode status = statusOf(ex);
    if (status.is5xxServerError()) {
      logFailure(exchange, status, ex);
    }
    return ErrorResponses.write(exchange, status);
  }

  /** Runs outside the correlation filter's Reactor context, so the MDC is set here. */
  private static void logFailure(ServerWebExchange exchange, HttpStatusCode status, Throwable ex) {
    String correlationId = exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER);
    try (MDC.MDCCloseable c =
        correlationId == null ? null : MDC.putCloseable(CorrelationId.MDC_KEY, correlationId)) {
      String method = exchange.getRequest().getMethod().name();
      String path = exchange.getRequest().getPath().value();
      if (status.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
        log.error("Gateway error {} for {} {}", status.value(), method, path, ex);
      } else {
        log.warn("Gateway error {} for {} {}: {}", status.value(), method, path, ex.toString());
      }
    }
  }

  static HttpStatusCode statusOf(Throwable ex) {
    if (ex instanceof ResponseStatusException rse) {
      return rse.getStatusCode();
    }
    if (hasCause(ex, TimeoutException.class)) {
      return HttpStatus.GATEWAY_TIMEOUT;
    }
    if (hasCause(ex, ConnectException.class)) {
      return HttpStatus.SERVICE_UNAVAILABLE;
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (type.isInstance(t)) {
        return true;
      }
    }
    return false;
  }
}
