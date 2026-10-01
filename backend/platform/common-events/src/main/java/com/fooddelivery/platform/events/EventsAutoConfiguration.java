package com.fooddelivery.platform.events;

import com.fooddelivery.platform.events.consumer.DeadLetterHeaders;
import com.fooddelivery.platform.events.consumer.IdempotentEventProcessor;
import com.fooddelivery.platform.events.consumer.ProcessedEventsPurger;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import com.fooddelivery.platform.events.outbox.OutboxPurger;
import com.fooddelivery.platform.events.outbox.OutboxRelay;
import com.fooddelivery.platform.persistence.migration.PlatformMigration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Event backbone for services (REQ-PLAT-005, REQ-PLAT-006 AC1). Off by default; a service enables
 * {@code fdp.events.outbox.enabled} when it publishes and {@code fdp.events.consumer.enabled} when
 * it consumes. Each feature brings its own table through a platform migration.
 */
@AutoConfiguration
@EnableConfigurationProperties(EventsProperties.class)
public class EventsAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "fdp.events.outbox.enabled", havingValue = "true")
  @EnableScheduling
  static class OutboxConfiguration {

    @Bean
    PlatformMigration outboxMigration() {
      return new PlatformMigration("outbox", "classpath:db/fdp/outbox");
    }

    @Bean
    OutboxPublisher outboxPublisher(
        NamedParameterJdbcTemplate jdbc,
        JsonMapper json,
        @Value("${spring.application.name}") String producer,
        ObjectProvider<EntityManagerFactory> entityManagerFactory,
        ObjectProvider<Tracer> tracer) {
      return new OutboxPublisher(
          jdbc, json, producer, Clock.systemUTC(), entityManagerFactory, tracer);
    }

    @Bean
    @SuppressWarnings("unchecked")
    OutboxRelay outboxRelay(
        NamedParameterJdbcTemplate jdbc,
        PlatformTransactionManager transactions,
        KafkaTemplate<?, ?> kafka,
        JsonMapper json,
        EventsProperties properties,
        ObjectProvider<MeterRegistry> meters) {
      return new OutboxRelay(
          jdbc,
          transactions,
          (KafkaTemplate<String, String>) kafka,
          json,
          properties.outbox(),
          meters.getIfAvailable(SimpleMeterRegistry::new),
          Clock.systemUTC());
    }

    @Bean
    OutboxPurger outboxPurger(JdbcTemplate jdbc, EventsProperties properties) {
      return new OutboxPurger(jdbc, properties.outbox().retention());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "fdp.events.consumer.enabled", havingValue = "true")
  @EnableScheduling
  static class ConsumerConfiguration {

    @Bean
    PlatformMigration processedEventsMigration() {
      return new PlatformMigration("consumer", "classpath:db/fdp/consumer");
    }

    @Bean
    IdempotentEventProcessor idempotentEventProcessor(
        JdbcTemplate jdbc, PlatformTransactionManager transactions, JsonMapper json) {
      return new IdempotentEventProcessor(jdbc, transactions, json);
    }

    /**
     * Blocking retries then {@code <topic>.dlt} on the same partition (docs/08 §5). Invalid
     * messages skip the retries. Spring Kafka's own exception message and stack trace headers are
     * left out because they may contain personal data; {@link DeadLetterHeaders} adds a sanitised
     * message instead.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler eventErrorHandler(
        KafkaTemplate<?, ?> kafka,
        EventsProperties properties,
        ObjectProvider<MeterRegistry> meters) {
      MeterRegistry registry = meters.getIfAvailable(SimpleMeterRegistry::new);
      DeadLetterPublishingRecoverer publisher =
          new DeadLetterPublishingRecoverer(
              kafka,
              (record, exception) ->
                  new TopicPartition(record.topic() + EventTopics.DLT_SUFFIX, record.partition()));
      publisher.excludeHeader(HeadersToAdd.EX_MSG, HeadersToAdd.EX_STACKTRACE);
      publisher.setHeadersFunction(new DeadLetterHeaders(Clock.systemUTC()));
      publisher.setFailIfSendResultIsError(true);

      EventsProperties.Consumer retry = properties.consumer();
      ExponentialBackOffWithMaxRetries backOff =
          new ExponentialBackOffWithMaxRetries(retry.maxRetries());
      backOff.setInitialInterval(retry.initialInterval().toMillis());
      backOff.setMultiplier(retry.multiplier());
      backOff.setMaxInterval(
          (long)
              (retry.initialInterval().toMillis()
                  * Math.pow(retry.multiplier(), Math.max(0, retry.maxRetries() - 1))));

      DefaultErrorHandler handler =
          new DefaultErrorHandler(
              (record, exception) -> {
                publisher.accept(record, exception);
                registry.counter("events.dead.lettered", "topic", record.topic()).increment();
              },
              backOff);
      handler.addNotRetryableExceptions(InvalidEventException.class);
      return handler;
    }

    @Bean
    ProcessedEventsPurger processedEventsPurger(JdbcTemplate jdbc, EventsProperties properties) {
      return new ProcessedEventsPurger(jdbc, properties.consumer().retention());
    }
  }
}
