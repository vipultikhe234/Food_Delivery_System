package com.fooddelivery.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Checks that config-repo/api-gateway.yml sends each context to its owning service
 * (docs/05-microservices.md §1.2), including the order-sensitive overlaps.
 */
@SpringBootTest(
    properties = {
      "CONFIG_IMPORT=file:"
          + GatewayIntegrationTest.CONFIG_REPO
          + "application.yml,file:"
          + GatewayIntegrationTest.CONFIG_REPO
          + "api-gateway.yml",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "server.port=0",
      "MANAGEMENT_PORT=0"
    })
class RouteTableTest {

  @Autowired RouteLocator routeLocator;

  @Test
  void pathsResolveToOwningService() {
    Map<String, String> expected =
        Map.ofEntries(
            Map.entry("/api/v1/auth/login", "lb://identity-service"),
            Map.entry("/.well-known/jwks.json", "lb://identity-service"),
            Map.entry("/api/v1/admin/roles", "lb://identity-service"),
            Map.entry("/api/v1/admin/users/u-1/roles", "lb://identity-service"),
            Map.entry("/api/v1/me/addresses", "lb://user-service"),
            Map.entry("/api/v1/restaurants/r-1", "lb://restaurant-service"),
            Map.entry("/api/v1/branches/b-1/hours", "lb://restaurant-service"),
            Map.entry("/api/v1/partner/devices/d-1", "lb://restaurant-service"),
            Map.entry("/api/v1/admin/restaurants/r-1/approve", "lb://restaurant-service"),
            Map.entry("/api/v1/branches/b-1/menu", "lb://catalog-service"),
            Map.entry("/api/v1/products/p-1", "lb://catalog-service"),
            Map.entry("/api/v1/partner/menu/branches/b-1/products", "lb://catalog-service"),
            Map.entry("/api/v1/cart/current", "lb://cart-service"),
            Map.entry("/api/v1/admin/coupons", "lb://promotion-service"),
            Map.entry("/api/v1/partner/offers", "lb://promotion-service"),
            Map.entry("/api/v1/orders/o-1", "lb://order-service"),
            Map.entry("/api/v1/partner/orders/o-1/accept", "lb://order-service"),
            Map.entry("/api/v1/admin/orders", "lb://order-service"),
            Map.entry("/api/v1/webhooks/payments/razorpay", "lb://payment-service"),
            Map.entry("/api/v1/admin/refunds", "lb://payment-service"),
            Map.entry("/api/v1/admin/partners/dp-1", "lb://delivery-service"),
            Map.entry("/api/v1/serviceability", "lb://location-service"),
            Map.entry("/api/v1/admin/notification-templates", "lb://notification-service"),
            Map.entry("/api/v1/admin/reviews/rv-1/hide", "lb://review-service"),
            Map.entry("/api/v1/admin/complaints", "lb://admin-service"),
            Map.entry("/api/v1/admin/dashboard", "lb://admin-service"),
            Map.entry("/api/v1/assistant/chat", "lb://ai-service"),
            Map.entry("/ws/orders", "lb:ws://realtime-service"),
            Map.entry("/api/v1/qr/sessions", "lb://pos-service"),
            Map.entry("/api/v1/kitchen/tickets", "lb://kitchen-service"),
            Map.entry("/api/v1/recipes/rc-1", "lb://inventory-service"),
            Map.entry("/api/v1/procurement/purchase-orders", "lb://procurement-service"));

    List<Route> routes = routeLocator.getRoutes().collectList().block();
    expected.forEach((path, uri) -> assertThat(firstMatch(routes, path)).as(path).isEqualTo(uri));
  }

  @Test
  void internalPathsAreNeverRouted() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(firstMatch(routes, "/internal/v1/tokens/service")).isNull();
    assertThat(firstMatch(routes, "/actuator/health")).isNull();
  }

  private static String firstMatch(List<Route> routes, String path) {
    return Flux.fromIterable(routes)
        .concatMap(
            route ->
                Mono.from(
                        route
                            .getPredicate()
                            .apply(MockServerWebExchange.from(MockServerHttpRequest.get(path))))
                    .filter(Boolean::booleanValue)
                    .map(matched -> route.getUri().toString()))
        .next()
        .block();
  }
}
