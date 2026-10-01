package com.fooddelivery.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class EventTopicsTest {

  @Test
  void topicsKeepSevenDaysAndDeadLettersThirty() {
    NewTopic topic = EventTopics.topic("order.events.v1", 3, (short) 1);
    NewTopic dlt = EventTopics.deadLetter("order.events.v1", 3, (short) 1);

    assertThat(topic.name()).isEqualTo("order.events.v1");
    assertThat(topic.configs()).containsEntry(TopicConfig.RETENTION_MS_CONFIG, "604800000");
    assertThat(dlt.name()).isEqualTo("order.events.v1.dlt");
    assertThat(dlt.numPartitions()).isEqualTo(topic.numPartitions());
    assertThat(dlt.configs()).containsEntry(TopicConfig.RETENTION_MS_CONFIG, "2592000000");
  }

  @Test
  void kafkaDefaultsAreDurableAndOverridable() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("spring.kafka.producer.properties[linger.ms]", "20");

    new KafkaDefaultsEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.kafka.producer.acks")).isEqualTo("all");
    assertThat(environment.getProperty("spring.kafka.producer.properties[enable.idempotence]"))
        .isEqualTo("true");
    assertThat(environment.getProperty("spring.kafka.producer.compression-type")).isEqualTo("lz4");
    assertThat(environment.getProperty("spring.kafka.consumer.enable-auto-commit"))
        .isEqualTo("false");
    assertThat(environment.getProperty("spring.kafka.producer.properties[linger.ms]"))
        .isEqualTo("20");
  }
}
