package com.fooddelivery.platform.observability.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the request's correlation id into the MDC and echoes it on the response (REQ-PLAT-001 AC3,
 * REQ-OBS-001 AC1). The gateway always sets the header; direct calls inside the cluster get a new
 * id.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = CorrelationId.acceptOrCreate(request.getHeader(CorrelationId.HEADER));
    response.setHeader(CorrelationId.HEADER, correlationId);
    try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationId.MDC_KEY, correlationId)) {
      chain.doFilter(request, response);
    }
  }
}
