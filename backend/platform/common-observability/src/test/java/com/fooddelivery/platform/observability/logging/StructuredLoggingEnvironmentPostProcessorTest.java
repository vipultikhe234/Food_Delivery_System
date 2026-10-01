package com.fooddelivery.platform.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class StructuredLoggingEnvironmentPostProcessorTest {

  private final StructuredLoggingEnvironmentPostProcessor processor =
      new StructuredLoggingEnvironmentPostProcessor();

  @Test
  void addsJsonDefaultsAtLowestPrecedence() {
    StandardEnvironment environment = new StandardEnvironment();

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(
            environment.getPropertySources().stream().reduce((a, b) -> b).orElseThrow().getName())
        .isEqualTo(StructuredLoggingEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    assertThat(environment.getProperty("logging.structured.json.customizer"))
        .isEqualTo(MaskingJsonMembersCustomizer.class.getName());
  }

  @Test
  void serviceConfigurationOverridesTheDefaults() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("logging.structured.format.console", "ecs");

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
  }

  @Test
  void plainFormatLeavesSpringBootDefaults() {
    MockEnvironment environment = new MockEnvironment().withProperty("fdp.logging.format", "plain");

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(
            environment
                .getPropertySources()
                .contains(StructuredLoggingEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
        .isFalse();
    assertThat(environment.getProperty("logging.structured.format.console")).isNull();
  }
}
