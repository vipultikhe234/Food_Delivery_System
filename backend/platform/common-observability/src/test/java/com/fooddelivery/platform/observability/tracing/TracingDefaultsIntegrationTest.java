package com.fooddelivery.platform.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class TracingDefaultsIntegrationTest {

  @Test
  void exporterIsOffByDefaultButTraceContextStillPropagates() {
    try (ConfigurableApplicationContext context = start()) {
      assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).isEmpty();

      Tracer tracer = context.getBean(Tracer.class);
      Propagator propagator = context.getBean(Propagator.class);
      Span span = tracer.nextSpan().name("outgoing").start();
      Map<String, String> carrier = new HashMap<>();
      propagator.inject(span.context(), carrier, Map::put);
      span.end();

      assertThat(carrier.get("traceparent"))
          .matches("00-" + span.context().traceId() + "-[0-9a-f]{16}-[0-9a-f]{2}");
    }
  }

  @Test
  void exporterIsCreatedWhenEnabled() {
    try (ConfigurableApplicationContext context =
        start(
            "--TRACING_EXPORT_ENABLED=true",
            "--OTLP_TRACES_ENDPOINT=http://127.0.0.1:1/v1/traces")) {
      assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).hasSize(1);
    }
  }

  private static ConfigurableApplicationContext start(String... args) {
    return new SpringApplicationBuilder(TestApplication.class)
        .web(WebApplicationType.NONE)
        .run(args);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}
}
