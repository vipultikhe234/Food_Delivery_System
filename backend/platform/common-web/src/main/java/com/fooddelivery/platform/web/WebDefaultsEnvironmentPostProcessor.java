package com.fooddelivery.platform.web;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lowest-precedence API defaults (docs/07-api-design.md §1 and §3): unknown JSON properties are
 * rejected, which prevents mass assignment, and Spring Boot's error output never carries exception
 * text or stack traces.
 */
public class WebDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String PROPERTY_SOURCE_NAME = "fdpWebDefaults";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults =
        Map.of(
            "spring.jackson.deserialization.fail-on-unknown-properties", "true",
            "server.error.include-stacktrace", "never",
            "server.error.include-message", "never",
            "server.error.include-binding-errors", "never",
            "server.error.include-exception", "false",
            "server.error.whitelabel.enabled", "false");
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
