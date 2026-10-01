package com.fooddelivery.platform.web.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.platform.web.client.FaultServer.Reply;
import com.fooddelivery.platform.web.client.HttpClientProperties.ClientSettings;
import com.fooddelivery.platform.web.error.CommonErrorCode;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class ResilientRestClientsTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.ofDefaults();
  private final RetryRegistry retries = RetryRegistry.ofDefaults();
  private FaultServer server;

  @BeforeEach
  void setUp() throws Exception {
    TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers).bindTo(meters);
    TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(meters);
    server = new FaultServer();
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void slowResponsesHitTheReadTimeoutAndAreRetriedForGet() {
    server.always(Reply.slow(Duration.ofSeconds(2)));
    RestClient client = client("menu", settings(Duration.ofMillis(200), 2, breakerOff()));

    long start = System.nanoTime();
    assertThatThrownBy(() -> get(client))
        .isInstanceOf(DownstreamUnavailableException.class)
        .satisfies(
            e ->
                assertThat(((DownstreamUnavailableException) e).errorCode())
                    .isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

    assertThat(server.requests()).isEqualTo(3);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void transientServerErrorsAreRetriedUntilSuccess() {
    server.then(Reply.status(503), Reply.status(502), Reply.ok());
    RestClient client = client("catalog", settings(Duration.ofSeconds(2), 2, breakerOff()));

    assertThat(get(client)).isEqualTo("reply 200");
    assertThat(server.requests()).isEqualTo(3);
    assertThat(
            meters
                .get("resilience4j.retry.calls")
                .tags("name", "catalog", "kind", "successful_with_retry")
                .functionCounter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void nonIdempotentRequestsAreNeverRetried() {
    server.always(Reply.status(500));
    RestClient client = client("payment-gateway", settings(Duration.ofSeconds(2), 2, breakerOff()));

    assertThatThrownBy(() -> client.post().uri("/charges").body("{}").retrieve().toBodilessEntity())
        .isInstanceOf(DownstreamUnavailableException.class);
    assertThat(server.requests()).isEqualTo(1);
  }

  @Test
  void postWithAnIdempotencyKeyIsRetried() {
    server.then(Reply.status(503), Reply.ok());
    RestClient client = client("payment-gateway", settings(Duration.ofSeconds(2), 2, breakerOff()));

    client
        .post()
        .uri("/charges")
        .header("Idempotency-Key", "k-1")
        .body("{}")
        .retrieve()
        .toBodilessEntity();

    assertThat(server.requests()).isEqualTo(2);
  }

  @Test
  void clientErrorsAreReturnedWithoutRetryAndDoNotTripTheBreaker() {
    server.always(Reply.status(404));
    RestClient client = client("user", settings(Duration.ofSeconds(2), 2, breaker(2)));

    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> get(client)).isInstanceOf(HttpClientErrorException.NotFound.class);
    }
    assertThat(server.requests()).isEqualTo(3);
    assertThat(circuitBreakers.circuitBreaker("user").getState())
        .isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void openCircuitFailsFastWithoutCallingTheDownstream() {
    server.always(Reply.status(500));
    RestClient client = client("maps", settings(Duration.ofSeconds(2), 0, breaker(4)));

    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> get(client)).isInstanceOf(DownstreamUnavailableException.class);
    }
    assertThat(circuitBreakers.circuitBreaker("maps").getState())
        .isEqualTo(CircuitBreaker.State.OPEN);

    assertThatThrownBy(() -> get(client))
        .isInstanceOf(DownstreamUnavailableException.class)
        .hasMessageContaining("circuit open");
    assertThat(server.requests()).isEqualTo(4);
    assertThat(
            meters
                .get("resilience4j.circuitbreaker.state")
                .tags("name", "maps", "state", "open")
                .gauge()
                .value())
        .isEqualTo(1.0);
  }

  @Test
  void bulkheadRejectsCallsAboveTheConcurrencyLimit() throws Exception {
    server.always(Reply.slow(Duration.ofMillis(800)));
    ClientSettings settings =
        new ClientSettings(
            Duration.ofMillis(500),
            Duration.ofSeconds(2),
            new HttpClientProperties.Retry(0, Duration.ofMillis(10), 2.0, 0.5),
            breakerOff(),
            new HttpClientProperties.Bulkhead(1));
    RestClient client = client("sms", settings);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> get(client), executor);
      Thread.sleep(200);
      assertThatThrownBy(() -> get(client))
          .isInstanceOf(DownstreamUnavailableException.class)
          .hasMessageContaining("bulkhead full");
      assertThat(first.get()).isEqualTo("reply 200");
    }
  }

  @Test
  void connectionFailuresBecomeDependencyUnavailable() {
    String closedPort = server.baseUrl();
    server.close();
    RestClient client =
        new ResilientRestClients(
                new HttpClientProperties(
                    Map.of("llm", settings(Duration.ofMillis(500), 1, breakerOff()))),
                circuitBreakers,
                retries,
                BulkheadRegistry.ofDefaults())
            .create("llm", RestClient.builder(), closedPort);

    assertThatThrownBy(() -> get(client)).isInstanceOf(DownstreamUnavailableException.class);
  }

  @Test
  void backoffIsExponentialWithJitter() {
    var interval =
        ResilientRestClients.retryConfig(ClientSettings.defaults()).getIntervalBiFunction();

    List<Long> first = List.of(interval.apply(1, null), interval.apply(1, null));
    long second = interval.apply(2, null);

    assertThat(first).allSatisfy(ms -> assertThat(ms).isBetween(100L, 300L));
    assertThat(second).isBetween(200L, 600L);
  }

  @Test
  void unknownClientsGetTheDocumentedDefaults() {
    ClientSettings defaults = new HttpClientProperties(Map.of()).settingsFor("anything");

    assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofMillis(500));
    assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(2));
    assertThat(defaults.retry().maxRetries()).isEqualTo(2);
    assertThat(defaults.circuitBreaker().slidingWindowSize()).isEqualTo(50);
    assertThat(defaults.circuitBreaker().openDuration()).isEqualTo(Duration.ofSeconds(30));
    assertThat(defaults.bulkhead().maxConcurrentCalls()).isEqualTo(50);
    assertThat(HttpStatus.valueOf(CommonErrorCode.DEPENDENCY_UNAVAILABLE.status().value()))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  private String get(RestClient client) {
    return client.get().uri("/items").retrieve().body(String.class);
  }

  private RestClient client(String name, ClientSettings settings) {
    return new ResilientRestClients(
            new HttpClientProperties(Map.of(name, settings)),
            circuitBreakers,
            retries,
            BulkheadRegistry.ofDefaults())
        .create(name, RestClient.builder(), server.baseUrl());
  }

  private static ClientSettings settings(
      Duration readTimeout, int maxRetries, HttpClientProperties.CircuitBreaker breaker) {
    return new ClientSettings(
        Duration.ofMillis(500),
        readTimeout,
        new HttpClientProperties.Retry(maxRetries, Duration.ofMillis(10), 2.0, 0.5),
        breaker,
        new HttpClientProperties.Bulkhead(50));
  }

  private static HttpClientProperties.CircuitBreaker breakerOff() {
    return new HttpClientProperties.CircuitBreaker(
        false, 50, 50, 50f, Duration.ofSeconds(2), 50f, Duration.ofSeconds(30), 5);
  }

  private static HttpClientProperties.CircuitBreaker breaker(int window) {
    return new HttpClientProperties.CircuitBreaker(
        true, window, window, 50f, Duration.ofSeconds(2), 50f, Duration.ofSeconds(30), 1);
  }
}
