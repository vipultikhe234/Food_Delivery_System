package com.fooddelivery.platform.testsupport;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Container factories pinned to the same images as {@code
 * infrastructure/docker/docker-compose.yml}, so tests run against the versions developers use
 * locally.
 */
public final class Containers {

  public static final String POSTGRES_IMAGE = "postgis/postgis:17-3.6-alpine";
  public static final String KAFKA_IMAGE = "apache/kafka:4.3.1";

  private Containers() {}

  public static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(
        DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));
  }

  public static KafkaContainer kafka() {
    return new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));
  }
}
