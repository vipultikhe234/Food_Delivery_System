package com.fooddelivery.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fooddelivery.platform.events.consumer.IdempotentEventProcessor;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import com.fooddelivery.platform.events.outbox.OutboxRelay;
import com.fooddelivery.platform.persistence.migration.PlatformMigration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class EventsAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EventsAutoConfiguration.class))
          .withPropertyValues("spring.application.name=wiring-test")
          .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
          .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
          .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
          .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
          .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

  @Test
  void nothingIsActiveUntilAServiceOptsIn() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(OutboxPublisher.class);
          assertThat(context).doesNotHaveBean(OutboxRelay.class);
          assertThat(context).doesNotHaveBean(IdempotentEventProcessor.class);
          assertThat(context).doesNotHaveBean(CommonErrorHandler.class);
          assertThat(context).doesNotHaveBean(PlatformMigration.class);
        });
  }

  @Test
  void publishersGetTheOutboxAndConsumersGetDeduplicationAndTheDeadLetterPolicy() {
    runner
        .withPropertyValues("fdp.events.outbox.enabled=true", "fdp.events.consumer.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(OutboxPublisher.class);
              assertThat(context).hasSingleBean(OutboxRelay.class);
              assertThat(context).hasSingleBean(IdempotentEventProcessor.class);
              assertThat(context).hasSingleBean(CommonErrorHandler.class);
              assertThat(context.getBeansOfType(PlatformMigration.class).values())
                  .extracting(PlatformMigration::historyTable)
                  .containsExactlyInAnyOrder(
                      "fdp_outbox_schema_history", "fdp_consumer_schema_history");
            });
  }
}
