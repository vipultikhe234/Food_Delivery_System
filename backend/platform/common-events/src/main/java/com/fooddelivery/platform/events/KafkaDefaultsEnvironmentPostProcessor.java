package com.fooddelivery.platform.events;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lowest-precedence Kafka client defaults (docs/08 §2 and §4): durable idempotent producer, JSON
 * envelopes as strings, manual offset commits after each record.
 *
 * <p>Producer timeouts are shorter than the Kafka defaults (60 s block, 120 s delivery) because the
 * outbox relay holds a database transaction while it waits for acknowledgements. Listener
 * observation continues the trace from the {@code traceparent} header written by the outbox.
 */
public class KafkaDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String PROPERTY_SOURCE_NAME = "fdpKafkaDefaults";
  private static final String STRING_SERIALIZER =
      "org.apache.kafka.common.serialization.StringSerializer";
  private static final String STRING_DESERIALIZER =
      "org.apache.kafka.common.serialization.StringDeserializer";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults =
        Map.ofEntries(
            Map.entry("spring.kafka.producer.acks", "all"),
            Map.entry("spring.kafka.producer.compression-type", "lz4"),
            Map.entry("spring.kafka.producer.key-serializer", STRING_SERIALIZER),
            Map.entry("spring.kafka.producer.value-serializer", STRING_SERIALIZER),
            Map.entry("spring.kafka.producer.properties[enable.idempotence]", "true"),
            Map.entry("spring.kafka.producer.properties[linger.ms]", "5"),
            Map.entry("spring.kafka.producer.properties[max.block.ms]", "5000"),
            Map.entry("spring.kafka.producer.properties[request.timeout.ms]", "5000"),
            Map.entry("spring.kafka.producer.properties[delivery.timeout.ms]", "15000"),
            Map.entry("spring.kafka.consumer.key-deserializer", STRING_DESERIALIZER),
            Map.entry("spring.kafka.consumer.value-deserializer", STRING_DESERIALIZER),
            Map.entry("spring.kafka.consumer.enable-auto-commit", "false"),
            Map.entry("spring.kafka.consumer.auto-offset-reset", "earliest"),
            Map.entry("spring.kafka.listener.ack-mode", "record"),
            Map.entry("spring.kafka.listener.observation-enabled", "true"));
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
