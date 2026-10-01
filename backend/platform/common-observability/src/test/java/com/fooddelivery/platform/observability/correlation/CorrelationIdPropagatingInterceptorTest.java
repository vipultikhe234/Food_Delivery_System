package com.fooddelivery.platform.observability.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CorrelationIdPropagatingInterceptorTest {

  @Test
  void copiesTheCurrentCorrelationIdToOutgoingCalls() {
    RestClient.Builder builder =
        RestClient.builder().requestInterceptor(new CorrelationIdPropagatingInterceptor());
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://order-service/internal/v1/ping"))
        .andExpect(header(CorrelationId.HEADER, "corr-1"))
        .andRespond(withSuccess());

    try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationId.MDC_KEY, "corr-1")) {
      builder
          .build()
          .get()
          .uri("http://order-service/internal/v1/ping")
          .retrieve()
          .toBodilessEntity();
    }

    server.verify();
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }
}
