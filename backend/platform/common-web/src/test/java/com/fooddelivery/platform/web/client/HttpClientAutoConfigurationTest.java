package com.fooddelivery.platform.web.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.platform.web.HttpClientAutoConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class HttpClientAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class))
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

  @Test
  void bindsPerClientOverridesOnTopOfTheDefaults() {
    runner
        .withPropertyValues(
            "fdp.http.clients.payment-gateway.read-timeout=10s",
            "fdp.http.clients.payment-gateway.retry.max-retries=0")
        .run(
            context -> {
              HttpClientProperties.ClientSettings settings =
                  context.getBean(HttpClientProperties.class).settingsFor("payment-gateway");
              assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(10));
              assertThat(settings.retry().maxRetries()).isZero();
              assertThat(settings.connectTimeout()).isEqualTo(Duration.ofMillis(500));
              assertThat(settings.circuitBreaker().failureRateThreshold()).isEqualTo(50f);
            });
  }

  @Test
  void exportsBreakerMetricsForCreatedClients() {
    runner.run(
        context -> {
          context
              .getBean(ResilientRestClients.class)
              .create("search", RestClient.builder(), "http://localhost:1");

          assertThat(context.getBean(CircuitBreakerRegistry.class).find("search")).isPresent();
          assertThat(
                  context
                      .getBean(MeterRegistry.class)
                      .find("resilience4j.circuitbreaker.state")
                      .tag("name", "search")
                      .gauges())
              .isNotEmpty();
        });
  }
}
