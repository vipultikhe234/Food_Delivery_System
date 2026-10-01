package com.fooddelivery.gateway.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Headers Spring Security does not write for an HTTP-only hop behind the TLS ingress
 * (docs/09-security.md §6.1). Set before the chain so error and 401 responses carry them too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class EdgeHeadersWebFilter implements WebFilter {

  static final String HSTS = "max-age=31536000; includeSubDomains";
  static final String PERMISSIONS_POLICY = "geolocation=(), camera=(), microphone=(), payment=()";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    HttpHeaders headers = exchange.getResponse().getHeaders();
    headers.set("Strict-Transport-Security", HSTS);
    headers.set("Permissions-Policy", PERMISSIONS_POLICY);
    return chain.filter(exchange);
  }
}
