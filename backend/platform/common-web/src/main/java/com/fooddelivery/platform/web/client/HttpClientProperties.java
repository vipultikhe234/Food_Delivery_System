package com.fooddelivery.platform.web.client;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-downstream settings under {@code fdp.http.clients.<name>}. Unset values fall back to the
 * defaults of docs/14-disaster-recovery.md §6.
 */
@ConfigurationProperties("fdp.http")
public record HttpClientProperties(Map<String, ClientSettings> clients) {

  public HttpClientProperties {
    clients = clients == null ? Map.of() : Map.copyOf(clients);
  }

  public ClientSettings settingsFor(String name) {
    return clients.getOrDefault(name, ClientSettings.defaults());
  }

  public record ClientSettings(
      @DefaultValue("500ms") Duration connectTimeout,
      @DefaultValue("2s") Duration readTimeout,
      @DefaultValue Retry retry,
      @DefaultValue CircuitBreaker circuitBreaker,
      @DefaultValue Bulkhead bulkhead) {

    public static ClientSettings defaults() {
      return new ClientSettings(
          Duration.ofMillis(500),
          Duration.ofSeconds(2),
          Retry.defaults(),
          CircuitBreaker.defaults(),
          Bulkhead.defaults());
    }
  }

  /**
   * Applies only to idempotent requests: GET, HEAD, OPTIONS, or any request with an
   * Idempotency-Key.
   */
  public record Retry(
      @DefaultValue("2") int maxRetries,
      @DefaultValue("200ms") Duration initialBackoff,
      @DefaultValue("2.0") double multiplier,
      @DefaultValue("0.5") double jitter) {

    static Retry defaults() {
      return new Retry(2, Duration.ofMillis(200), 2.0, 0.5);
    }
  }

  public record CircuitBreaker(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("50") int slidingWindowSize,
      @DefaultValue("50") int minimumCalls,
      @DefaultValue("50") float failureRateThreshold,
      @DefaultValue("2s") Duration slowCallDuration,
      @DefaultValue("50") float slowCallRateThreshold,
      @DefaultValue("30s") Duration openDuration,
      @DefaultValue("5") int halfOpenCalls) {

    static CircuitBreaker defaults() {
      return new CircuitBreaker(
          true, 50, 50, 50f, Duration.ofSeconds(2), 50f, Duration.ofSeconds(30), 5);
    }
  }

  public record Bulkhead(@DefaultValue("50") int maxConcurrentCalls) {

    static Bulkhead defaults() {
      return new Bulkhead(50);
    }
  }
}
