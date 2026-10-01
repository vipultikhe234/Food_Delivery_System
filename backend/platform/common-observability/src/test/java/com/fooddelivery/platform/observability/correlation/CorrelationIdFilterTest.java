package com.fooddelivery.platform.observability.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void keepsAValidIncomingIdAndExposesItInMdcAndResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    request.addHeader(CorrelationId.HEADER, "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seenInMdc = new AtomicReference<>();

    filter.doFilter(request, response, new MockFilterChain(new NoOpServlet(seenInMdc)));

    assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("abc-123");
    assertThat(seenInMdc.get()).isEqualTo("abc-123");
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }

  @Test
  void replacesAMissingOrMalformedId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    request.addHeader(CorrelationId.HEADER, "bad value\n{\"inject\":1}");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String id = response.getHeader(CorrelationId.HEADER);
    assertThat(id).isNotBlank().doesNotContain("inject");
    assertThat(CorrelationId.isValid(id)).isTrue();
  }

  private static final class NoOpServlet extends jakarta.servlet.http.HttpServlet {
    private final transient AtomicReference<String> seen;

    NoOpServlet(AtomicReference<String> seen) {
      this.seen = seen;
    }

    @Override
    protected void service(
        jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
      seen.set(MDC.get(CorrelationId.MDC_KEY));
    }
  }
}
