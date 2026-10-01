package com.fooddelivery.platform.observability.correlation;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Copies the current correlation id onto outgoing service-to-service HTTP calls. */
public class CorrelationIdPropagatingInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String correlationId = MDC.get(CorrelationId.MDC_KEY);
    if (correlationId != null && !request.getHeaders().containsHeader(CorrelationId.HEADER)) {
      request.getHeaders().set(CorrelationId.HEADER, correlationId);
    }
    return execution.execute(request, body);
  }
}
