package com.fooddelivery.platform.web.client;

import com.fooddelivery.platform.web.client.HttpClientProperties.ClientSettings;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Creates {@link RestClient}s for named downstreams with explicit timeouts and the resilience
 * policy from {@code fdp.http.clients.<name>} (REQ-PLAT-007).
 *
 * <p>Pass a {@code @LoadBalanced} builder to call other services by logical name ({@code
 * http://order-service}); pass the plain builder for external providers.
 */
public class ResilientRestClients {

  private final HttpClientProperties properties;
  private final CircuitBreakerRegistry circuitBreakers;
  private final RetryRegistry retries;
  private final BulkheadRegistry bulkheads;

  public ResilientRestClients(
      HttpClientProperties properties,
      CircuitBreakerRegistry circuitBreakers,
      RetryRegistry retries,
      BulkheadRegistry bulkheads) {
    this.properties = properties;
    this.circuitBreakers = circuitBreakers;
    this.retries = retries;
    this.bulkheads = bulkheads;
  }

  public RestClient create(String name, RestClient.Builder builder, String baseUrl) {
    ClientSettings settings = properties.settingsFor(name);
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build());
    requestFactory.setReadTimeout(settings.readTimeout());

    ResilienceInterceptor resilience =
        new ResilienceInterceptor(
            name,
            bulkheads.bulkhead(name, bulkheadConfig(settings)),
            circuitBreaker(name, settings),
            retries.retry(name, retryConfig(settings)));

    return builder
        .clone()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .requestInterceptor(resilience)
        .build();
  }

  private CircuitBreaker circuitBreaker(String name, ClientSettings settings) {
    HttpClientProperties.CircuitBreaker cb = settings.circuitBreaker();
    if (!cb.enabled()) {
      CircuitBreaker disabled = CircuitBreaker.ofDefaults(name);
      disabled.transitionToDisabledState();
      return disabled;
    }
    return circuitBreakers.circuitBreaker(
        name,
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(cb.slidingWindowSize())
            .minimumNumberOfCalls(cb.minimumCalls())
            .failureRateThreshold(cb.failureRateThreshold())
            .slowCallDurationThreshold(cb.slowCallDuration())
            .slowCallRateThreshold(cb.slowCallRateThreshold())
            .waitDurationInOpenState(cb.openDuration())
            .permittedNumberOfCallsInHalfOpenState(cb.halfOpenCalls())
            .build());
  }

  static RetryConfig retryConfig(ClientSettings settings) {
    HttpClientProperties.Retry retry = settings.retry();
    return RetryConfig.custom()
        .maxAttempts(retry.maxRetries() + 1)
        .intervalFunction(
            IntervalFunction.ofExponentialRandomBackoff(
                retry.initialBackoff(), retry.multiplier(), retry.jitter()))
        .retryExceptions(ResilienceInterceptor.FailedAttempt.class)
        .ignoreExceptions(CallNotPermittedException.class, BulkheadFullException.class)
        .build();
  }

  private static BulkheadConfig bulkheadConfig(ClientSettings settings) {
    return BulkheadConfig.custom()
        .maxConcurrentCalls(settings.bulkhead().maxConcurrentCalls())
        .maxWaitDuration(Duration.ZERO)
        .build();
  }
}
