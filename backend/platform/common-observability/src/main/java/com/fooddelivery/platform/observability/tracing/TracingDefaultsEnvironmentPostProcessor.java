package com.fooddelivery.platform.observability.tracing;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lowest-precedence tracing defaults for every service (docs/12-observability.md §3).
 *
 * <p>Only the OTLP exporter is switched off by default: {@code management.tracing.export.enabled}
 * must stay on, because turning it off also replaces the W3C propagator with a no-op and breaks
 * trace context between services.
 */
public class TracingDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String PROPERTY_SOURCE_NAME = "fdpTracingDefaults";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults =
        Map.of(
            "management.tracing.export.otlp.enabled", "${TRACING_EXPORT_ENABLED:false}",
            "management.opentelemetry.tracing.export.otlp.endpoint",
                "${OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}");
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
