package com.fooddelivery.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.gateway.support.StubBackend;
import com.fooddelivery.gateway.support.Tokens;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the gateway with the real config-repo files (routes, allow-list, JWT settings) against a
 * stub backend registered through simple discovery (REQ-PLAT-001 AC1-AC5).
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "CONFIG_IMPORT=file:"
          + GatewayIntegrationTest.CONFIG_REPO
          + "application.yml,file:"
          + GatewayIntegrationTest.CONFIG_REPO
          + "api-gateway.yml",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "MANAGEMENT_PORT=0",
      "CORS_ALLOWED_ORIGINS=" + GatewayIntegrationTest.ALLOWED_ORIGIN
    })
class GatewayIntegrationTest {

  static final String CONFIG_REPO = "../../../infrastructure/config-repo/";
  static final String ALLOWED_ORIGIN = "http://localhost:5173";

  static final RSAKey SIGNING_KEY = Tokens.newKey("test-key-1");
  static final StubBackend BACKEND = startBackend("stub-1");
  static final StubBackend SECOND_RESTAURANT_INSTANCE = startBackend("stub-2");
  static final int CLOSED_PORT = freePort();

  @LocalServerPort int port;
  @LocalManagementPort int managementPort;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    for (String service :
        new String[] {"identity-service", "order-service", "restaurant-service"}) {
      registry.add(
          "spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
          BACKEND::baseUrl);
    }
    registry.add(
        "spring.cloud.discovery.client.simple.instances.restaurant-service[1].uri",
        SECOND_RESTAURANT_INSTANCE::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.payment-service[0].uri",
        () -> "http://127.0.0.1:" + CLOSED_PORT);
  }

  @AfterAll
  static void stopBackend() {
    BACKEND.close();
    SECOND_RESTAURANT_INSTANCE.close();
  }

  // --- AC2: authentication ---------------------------------------------------------------

  @Test
  void protectedRouteWithoutTokenReturns401InStandardFormat() throws Exception {
    HttpResponse<String> response = send(get("/api/v1/orders/o-1"));

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(ct -> assertThat(ct).startsWith("application/problem+json"));
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
    String correlationId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
    assertThat(response.body())
        .contains("\"status\":401")
        .contains("\"code\":\"UNAUTHENTICATED\"")
        .contains("\"instance\":\"/api/v1/orders/o-1\"")
        .contains("\"correlationId\":\"" + correlationId + "\"")
        .contains("\"timestamp\":\"");
  }

  @Test
  void invalidTokensAreRejected() throws Exception {
    Instant future = Instant.now().plus(Duration.ofMinutes(10));
    RSAKey otherKeySameKid = Tokens.newKey("test-key-1");
    String[] tokens = {
      "not-a-jwt",
      Tokens.sign(SIGNING_KEY, Tokens.claims("u-1", Tokens.ISSUER, "some-other-api", future)),
      Tokens.sign(
          SIGNING_KEY, Tokens.claims("u-1", "https://evil.example", Tokens.AUDIENCE, future)),
      Tokens.sign(
          SIGNING_KEY,
          Tokens.claims(
              "u-1", Tokens.ISSUER, Tokens.AUDIENCE, Instant.now().minus(Duration.ofMinutes(5)))),
      Tokens.sign(otherKeySameKid, Tokens.claims("u-1", Tokens.ISSUER, Tokens.AUDIENCE, future)),
    };
    for (String token : tokens) {
      HttpResponse<String> response =
          send(get("/api/v1/orders/o-1").header("Authorization", "Bearer " + token));
      assertThat(response.statusCode()).as(token).isEqualTo(401);
      assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }
  }

  @Test
  void publicReadIsAllowedWithoutTokenButWritesAreNot() throws Exception {
    HttpResponse<String> read = send(get("/api/v1/restaurants/r-1"));
    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(read.body()).startsWith("request: GET /api/v1/restaurants/r-1");

    HttpResponse<String> write =
        send(request("/api/v1/restaurants").POST(HttpRequest.BodyPublishers.ofString("{}")));
    assertThat(write.statusCode()).isEqualTo(401);
  }

  // --- AC1 + AC3: routing and correlation -------------------------------------------------

  @Test
  void validTokenIsRoutedWithCorrelationIdAndWithoutSpoofedIdentityHeaders() throws Exception {
    HttpResponse<String> response =
        send(
            get("/api/v1/orders/o-1?page=2")
                .header("Authorization", "Bearer " + Tokens.valid(SIGNING_KEY, "user-42"))
                .header("X-User-Id", "admin")
                .header("X-Internal-Service", "payment-service"));

    assertThat(response.statusCode()).isEqualTo(200);
    String correlationId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
    assertThat(response.body())
        .startsWith("request: GET /api/v1/orders/o-1\n")
        .contains("x-correlation-id: " + correlationId + "\n")
        .contains("authorization: Bearer ")
        .containsPattern("traceparent: 00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}\n")
        .doesNotContain("x-user-id")
        .doesNotContain("x-internal-service");
  }

  @Test
  void requestsAreBalancedAcrossInstancesOfTheLogicalService() throws Exception {
    Set<String> instances = new HashSet<>();
    for (int i = 0; i < 10; i++) {
      HttpResponse<String> response = send(get("/api/v1/restaurants/r-1"));
      assertThat(response.statusCode()).isEqualTo(200);
      response.headers().firstValue("X-Stub-Instance").ifPresent(instances::add);
    }
    assertThat(instances).containsExactlyInAnyOrder("stub-1", "stub-2");
  }

  @Test
  void wellFormedCorrelationIdIsKeptAndMalformedOneIsReplaced() throws Exception {
    HttpResponse<String> kept =
        send(get("/api/v1/restaurants/r-1").header("X-Correlation-Id", "client-abc_123.x"));
    assertThat(kept.headers().firstValue("X-Correlation-Id")).hasValue("client-abc_123.x");
    assertThat(kept.body()).contains("x-correlation-id: client-abc_123.x\n");

    HttpResponse<String> replaced =
        send(get("/api/v1/restaurants/r-1").header("X-Correlation-Id", "bad id<script>"));
    String newId = replaced.headers().firstValue("X-Correlation-Id").orElseThrow();
    assertThat(newId).matches("[0-9a-f-]{36}");
    assertThat(replaced.body()).contains("x-correlation-id: " + newId + "\n");
  }

  @Test
  void unknownAndInternalPathsReturn404() throws Exception {
    String token = Tokens.valid(SIGNING_KEY, "user-42");
    for (String path : new String[] {"/api/v1/does-not-exist", "/internal/v1/tokens/service"}) {
      HttpResponse<String> response = send(get(path).header("Authorization", "Bearer " + token));
      assertThat(response.statusCode()).as(path).isEqualTo(404);
      assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }
  }

  @Test
  void serviceWithoutInstancesReturns503() throws Exception {
    HttpResponse<String> response =
        send(
            get("/api/v1/cart/current")
                .header("Authorization", "Bearer " + Tokens.valid(SIGNING_KEY, "user-42")));

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
  }

  @Test
  void unreachableInstanceReturns503WithoutInternalDetails() throws Exception {
    HttpResponse<String> response =
        send(
            get("/api/v1/payments/p-1")
                .header("Authorization", "Bearer " + Tokens.valid(SIGNING_KEY, "user-42")));

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body())
        .contains("\"code\":\"SERVICE_UNAVAILABLE\"")
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(CLOSED_PORT))
        .doesNotContainIgnoringCase("exception")
        .doesNotContain("at com.")
        .doesNotContain("at org.");
  }

  @Test
  void healthProbesOnTheManagementPortNeedNoTokenAndActuatorIsNotPublic() throws Exception {
    for (String probe : new String[] {"liveness", "readiness"}) {
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(
                      URI.create(
                          "http://localhost:" + managementPort + "/actuator/health/" + probe))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as(probe).isEqualTo(200);
      assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    assertThat(send(get("/actuator/health")).statusCode()).isEqualTo(404);
    assertThat(send(get("/actuator/env")).statusCode()).isEqualTo(401);
  }

  // --- AC4: security headers --------------------------------------------------------------

  @Test
  void securityHeadersArePresentOnSuccessAndErrorResponses() throws Exception {
    for (HttpResponse<String> response :
        List.of(send(get("/api/v1/restaurants/r-1")), send(get("/api/v1/orders/o-1")))) {
      assertThat(response.headers().firstValue("Strict-Transport-Security"))
          .hasValue("max-age=31536000; includeSubDomains");
      assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
      assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
      assertThat(response.headers().firstValue("Referrer-Policy"))
          .hasValue("strict-origin-when-cross-origin");
      assertThat(response.headers().firstValue("Content-Security-Policy"))
          .hasValue("default-src 'none'; frame-ancestors 'none'");
      assertThat(response.headers().firstValue("Permissions-Policy")).isPresent();
    }
  }

  // --- AC5: CORS --------------------------------------------------------------------------

  @Test
  void corsAllowsOnlyConfiguredOriginsAndCredentialsOnlyOnRefresh() throws Exception {
    HttpResponse<String> allowed = send(preflight("/api/v1/orders", ALLOWED_ORIGIN));
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
        .hasValue(ALLOWED_ORIGIN);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();

    HttpResponse<String> refresh = send(preflight("/api/v1/auth/refresh", ALLOWED_ORIGIN));
    assertThat(refresh.headers().firstValue("Access-Control-Allow-Origin"))
        .hasValue(ALLOWED_ORIGIN);
    assertThat(refresh.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");

    HttpResponse<String> denied = send(preflight("/api/v1/orders", "https://evil.example"));
    assertThat(denied.statusCode()).isEqualTo(403);
    assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();

    HttpResponse<String> simpleRequest =
        send(get("/api/v1/restaurants/r-1").header("Origin", "https://evil.example"));
    assertThat(simpleRequest.statusCode()).isEqualTo(403);
  }

  // --- helpers ----------------------------------------------------------------------------

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .timeout(Duration.ofSeconds(10));
  }

  private HttpRequest.Builder get(String path) {
    return request(path).GET();
  }

  private HttpRequest.Builder preflight(String path, String origin) {
    return request(path)
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .header("Origin", origin)
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key");
  }

  private HttpResponse<String> send(HttpRequest.Builder builder)
      throws IOException, InterruptedException {
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static StubBackend startBackend(String instanceName) {
    try {
      return new StubBackend(SIGNING_KEY, instanceName);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
