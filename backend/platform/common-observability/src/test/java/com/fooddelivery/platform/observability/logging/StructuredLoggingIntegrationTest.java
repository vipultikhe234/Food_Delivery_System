package com.fooddelivery.platform.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.platform.observability.correlation.CorrelationId;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger("fdp.test");

  @Test
  void writesOneJsonObjectPerLineWithMandatoryFieldsAndMaskedValues(CapturedOutput output) {
    try (ConfigurableApplicationContext context = start()) {
      Tracer tracer = context.getBean(Tracer.class);
      Span span = tracer.nextSpan().name("test").start();
      try (Tracer.SpanInScope ignored = tracer.withSpan(span);
          MDC.MDCCloseable c =
              MDC.putCloseable(CorrelationId.MDC_KEY, "corr-9876543210-x")) { // gitleaks:allow
        log.info("otp sent to 9876543210 for rahul@example.com");
      } finally {
        span.end();
      }

      DocumentContext line = JsonPath.parse(findLine(output, "for r***@example.com"));
      assertThat(line.read("$.timestamp", String.class)).isNotBlank();
      assertThat(line.read("$.level", String.class)).isEqualTo("INFO");
      assertThat(line.read("$.service", String.class)).isEqualTo("logging-test");
      assertThat(line.read("$.env", String.class)).isEqualTo("test");
      assertThat(line.read("$.version", String.class)).isNotBlank();
      assertThat(line.read("$.logger", String.class)).isEqualTo("fdp.test");
      assertThat(line.read("$.thread", String.class)).isNotBlank();
      assertThat(line.read("$.traceId", String.class)).isEqualTo(span.context().traceId());
      assertThat(line.read("$.spanId", String.class)).isEqualTo(span.context().spanId());
      assertThat(line.read("$.correlationId", String.class)).isEqualTo("corr-9876543210-x");
      assertThat(line.read("$.message", String.class))
          .isEqualTo("otp sent to ******3210 for r***@example.com");
      assertThat(line.json().toString()).doesNotContain("@version", "level_value");
    }
  }

  private static ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(TestApplication.class)
        .web(WebApplicationType.NONE)
        .run("--spring.application.name=logging-test", "--fdp.environment=test");
  }

  private static String findLine(CapturedOutput output, String marker) {
    return Arrays.stream(output.getOut().split("\\R"))
        .filter(l -> l.contains(marker))
        .reduce((first, second) -> second)
        .orElseThrow(() -> new AssertionError("no log line containing " + marker));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}
}
