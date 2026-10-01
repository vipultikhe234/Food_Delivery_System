package com.fooddelivery.platform.web.client;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Wraps every outbound call in bulkhead, circuit breaker and, for idempotent requests only, retry
 * with exponential backoff and jitter (REQ-PLAT-007).
 *
 * <p>4xx responses are the caller's problem: they are returned unchanged, never retried, and count
 * as successful calls for the circuit breaker.
 */
class ResilienceInterceptor implements ClientHttpRequestInterceptor {

  static final String IDEMPOTENCY_KEY = "Idempotency-Key";
  private static final Set<HttpMethod> SAFE_METHODS =
      Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

  private final String client;
  private final Bulkhead bulkhead;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  ResilienceInterceptor(
      String client, Bulkhead bulkhead, CircuitBreaker circuitBreaker, Retry retry) {
    this.client = client;
    this.bulkhead = bulkhead;
    this.circuitBreaker = circuitBreaker;
    this.retry = retry;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
    try {
      if (isIdempotent(request)) {
        return retry.executeCheckedSupplier(() -> attempt(request, body, execution));
      }
      return attempt(request, body, execution);
    } catch (CallNotPermittedException e) {
      throw new DownstreamUnavailableException(client, "circuit open", e);
    } catch (BulkheadFullException e) {
      throw new DownstreamUnavailableException(client, "bulkhead full", e);
    } catch (FailedAttempt e) {
      throw new DownstreamUnavailableException(client, e.getMessage(), e.getCause());
    } catch (Throwable e) {
      throw new DownstreamUnavailableException(client, "unexpected client failure", e);
    }
  }

  static boolean isIdempotent(HttpRequest request) {
    return SAFE_METHODS.contains(request.getMethod())
        || request.getHeaders().containsHeader(IDEMPOTENCY_KEY);
  }

  private ClientHttpResponse attempt(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
    bulkhead.acquirePermission();
    try {
      circuitBreaker.acquirePermission();
      long start = System.nanoTime();
      ClientHttpResponse response;
      try {
        response = execution.execute(request, body);
      } catch (IOException e) {
        circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
        throw new FailedAttempt(e.getClass().getSimpleName(), e);
      } catch (RuntimeException e) {
        circuitBreaker.releasePermission();
        throw e;
      }
      long elapsed = System.nanoTime() - start;
      int status = statusOf(response);
      if (status >= 500) {
        response.close();
        FailedAttempt failure = new FailedAttempt("status " + status, null);
        circuitBreaker.onError(elapsed, TimeUnit.NANOSECONDS, failure);
        throw failure;
      }
      circuitBreaker.onSuccess(elapsed, TimeUnit.NANOSECONDS);
      return response;
    } finally {
      bulkhead.onComplete();
    }
  }

  private static int statusOf(ClientHttpResponse response) {
    try {
      return response.getStatusCode().value();
    } catch (IOException e) {
      throw new FailedAttempt("unreadable status", e);
    }
  }

  /** One failed attempt; retryable when the request is idempotent. */
  static final class FailedAttempt extends RuntimeException {
    FailedAttempt(String message, Throwable cause) {
      super(message, cause, false, false);
    }
  }
}
