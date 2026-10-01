package com.fooddelivery.platform.events;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic declarations for the owning service (docs/08 §2). Brokers do not auto-create topics, so a
 * producer declares its topic and a consumer declares the DLT of each topic it consumes.
 *
 * <pre>{@code
 * @Bean NewTopic orderEvents(@Value("${fdp.kafka.partitions:3}") int partitions,
 *                           @Value("${fdp.kafka.replicas:1}") short replicas) {
 *   return EventTopics.topic("order.events.v1", partitions, replicas);
 * }
 * }</pre>
 */
public final class EventTopics {

  public static final String DLT_SUFFIX = ".dlt";
  static final Duration RETENTION = Duration.ofDays(7);
  static final Duration DLT_RETENTION = Duration.ofDays(30);

  private EventTopics() {}

  public static NewTopic topic(String name, int partitions, short replicas) {
    return build(name, partitions, replicas, RETENTION);
  }

  /** The DLT has the same partition count as its source so records keep their partition. */
  public static NewTopic deadLetter(String sourceTopic, int partitions, short replicas) {
    return build(sourceTopic + DLT_SUFFIX, partitions, replicas, DLT_RETENTION);
  }

  private static NewTopic build(String name, int partitions, short replicas, Duration retention) {
    return TopicBuilder.name(name)
        .partitions(partitions)
        .replicas(replicas)
        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis()))
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
        .build();
  }
}
