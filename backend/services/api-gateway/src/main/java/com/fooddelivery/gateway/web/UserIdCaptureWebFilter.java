package com.fooddelivery.gateway.web;

import java.security.Principal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Records the authenticated subject for the access log. Runs after Spring Security's filter chain
 * (order -100), because only then is the principal resolved.
 */
@Component
@Order(-50)
class UserIdCaptureWebFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return exchange
        .getPrincipal()
        .map(Principal::getName)
        .doOnNext(
            name -> exchange.getAttributes().put(CorrelationIdWebFilter.USER_ID_ATTRIBUTE, name))
        .then(Mono.defer(() -> chain.filter(exchange)));
  }
}
