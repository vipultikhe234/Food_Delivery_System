package com.fooddelivery.platform.observability.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Sets the platform's JSON log format as the lowest-precedence default for every service, so that
 * logs from all services share one schema (docs/12-observability.md §1).
 *
 * <p>Set {@code FDP_LOGGING_FORMAT=plain} to get Spring Boot's human-readable console output when
 * running a service from the IDE.
 */
public class StructuredLoggingEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  static final String PROPERTY_SOURCE_NAME = "fdpStructuredLoggingDefaults";
  static final String FORMAT_PROPERTY = "fdp.logging.format";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if ("plain".equalsIgnoreCase(environment.getProperty(FORMAT_PROPERTY, "json"))) {
      return;
    }
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("logging.structured.format.console", "logstash");
    defaults.put("logging.structured.json.rename[@timestamp]", "timestamp");
    defaults.put("logging.structured.json.rename[logger_name]", "logger");
    defaults.put("logging.structured.json.rename[thread_name]", "thread");
    defaults.put("logging.structured.json.exclude", "@version,level_value");
    defaults.put("logging.structured.json.add.service", "${spring.application.name:unknown}");
    defaults.put("logging.structured.json.add.env", "${fdp.environment:local}");
    defaults.put("logging.structured.json.add.version", buildVersion());
    defaults.put(
        "logging.structured.json.customizer", MaskingJsonMembersCustomizer.class.getName());
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
  }

  private static String buildVersion() {
    try (InputStream in =
        StructuredLoggingEnvironmentPostProcessor.class
            .getClassLoader()
            .getResourceAsStream("META-INF/build-info.properties")) {
      if (in == null) {
        return "unknown";
      }
      Properties properties = new Properties();
      properties.load(in);
      return properties.getProperty("build.version", "unknown");
    } catch (IOException ex) {
      return "unknown";
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
