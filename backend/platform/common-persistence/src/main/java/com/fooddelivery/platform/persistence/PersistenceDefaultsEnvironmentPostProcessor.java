package com.fooddelivery.platform.persistence;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lowest-precedence persistence defaults: Flyway owns the schema and Hibernate only validates it
 * (docs/06 §1.1), timestamps are written in UTC, and no lazy loading happens in the web layer.
 */
public class PersistenceDefaultsEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  static final String PROPERTY_SOURCE_NAME = "fdpPersistenceDefaults";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults =
        Map.of(
            "spring.jpa.hibernate.ddl-auto", "validate",
            "spring.jpa.open-in-view", "false",
            "spring.jpa.properties.hibernate.jdbc.time_zone", "UTC");
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
