package com.fooddelivery.gateway.web;

import com.fooddelivery.platform.observability.correlation.CorrelationId;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * First filter for every request: fixes the correlation id, removes headers that only the platform
 * may set, and writes the access log line (docs/12-observability.md §1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

  public static final String USER_ID_ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".userId";

  /**
   * Identity headers derived from the token downstream; a client must never be able to set them.
   */
  static final List<String> SPOOFABLE_HEADER_PREFIXES =
      List.of("x-user-", "x-internal-", "x-auth-", "x-roles", "x-permissions", "x-tenant-");

  private static final Logger accessLog = LoggerFactory.getLogger("fdp.gateway.access");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    long startNanos = System.nanoTime();
    ServerHttpRequest incoming = exchange.getRequest();
    String correlationId =
        CorrelationId.acceptOrCreate(incoming.getHeaders().getFirst(CorrelationId.HEADER));

    ServerHttpRequest request =
        incoming
            .mutate()
            .headers(
                headers -> {
                  stripSpoofableHeaders(headers);
                  headers.set(CorrelationId.HEADER, correlationId);
                })
            .build();
    exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
    ServerWebExchange mutated = exchange.mutate().request(request).build();

    return chain
        .filter(mutated)
        .contextWrite(ctx -> ctx.put(CorrelationId.MDC_KEY, correlationId))
        .doFinally(signal -> logAccess(mutated, correlationId, startNanos));
  }

  static void stripSpoofableHeaders(HttpHeaders headers) {
    List<String> names =
        headers.headerNames().stream()
            .filter(
                name -> {
                  String lower = name.toLowerCase(Locale.ROOT);
                  return SPOOFABLE_HEADER_PREFIXES.stream().anyMatch(lower::startsWith);
                })
            .toList();
    names.forEach(headers::remove);
  }

  private static void logAccess(ServerWebExchange exchange, String correlationId, long start) {
    if (!accessLog.isInfoEnabled()) {
      return;
    }
    ServerHttpRequest request = exchange.getRequest();
    HttpStatusCode status = exchange.getResponse().getStatusCode();
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    String userId = exchange.getAttribute(USER_ID_ATTRIBUTE);
    long latencyMs = (System.nanoTime() - start) / 1_000_000;
    try (MDC.MDCCloseable c = MDC.putCloseable(CorrelationId.MDC_KEY, correlationId);
        MDC.MDCCloseable u = userId == null ? null : MDC.putCloseable("userId", userId)) {
      accessLog
          .atInfo()
          .addKeyValue("method", request.getMethod().name())
          .addKeyValue("path", request.getPath().value())
          .addKeyValue("status", status == null ? 0 : status.value())
          .addKeyValue("latencyMs", latencyMs)
          .addKeyValue("route", route == null ? "-" : route.getId())
          .addKeyValue("clientApp", headerOrDash(request, "X-Client-App"))
          .addKeyValue("clientVersion", headerOrDash(request, "X-Client-Version"))
          .log(
              "{} {} {} {}ms",
              request.getMethod().name(),
              request.getPath().value(),
              status == null ? 0 : status.value(),
              latencyMs);
    }
  }

  private static String headerOrDash(ServerHttpRequest request, String name) {
    String value = request.getHeaders().getFirst(name);
    return value == null || value.isBlank() ? "-" : value;
  }
}
