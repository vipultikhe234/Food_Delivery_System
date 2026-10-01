package com.fooddelivery.platform.web;

import com.fooddelivery.platform.web.client.HttpClientProperties;
import com.fooddelivery.platform.web.client.ResilientRestClients;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Resilient outbound HTTP clients. Breaker state, retry counts and bulkhead usage are exported as
 * {@code resilience4j_*} metrics when a meter registry exists (REQ-PLAT-007 AC4).
 */
@AutoConfiguration
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  CircuitBreakerRegistry fdpCircuitBreakerRegistry(ObjectProvider<MeterRegistry> meters) {
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
    meters.ifAvailable(
        m -> TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(m));
    return registry;
  }

  @Bean
  @ConditionalOnMissingBean
  RetryRegistry fdpRetryRegistry(ObjectProvider<MeterRegistry> meters) {
    RetryRegistry registry = RetryRegistry.ofDefaults();
    meters.ifAvailable(m -> TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(m));
    return registry;
  }

  @Bean
  @ConditionalOnMissingBean
  BulkheadRegistry fdpBulkheadRegistry(ObjectProvider<MeterRegistry> meters) {
    BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
    meters.ifAvailable(m -> TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(m));
    return registry;
  }

  @Bean
  @ConditionalOnMissingBean
  ResilientRestClients resilientRestClients(
      HttpClientProperties properties,
      CircuitBreakerRegistry circuitBreakers,
      RetryRegistry retries,
      BulkheadRegistry bulkheads) {
    return new ResilientRestClients(properties, circuitBreakers, retries, bulkheads);
  }
}
