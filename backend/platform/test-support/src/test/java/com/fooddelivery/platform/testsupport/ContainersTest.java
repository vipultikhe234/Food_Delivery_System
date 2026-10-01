package com.fooddelivery.platform.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContainersTest {

  private static final Path COMPOSE = Path.of("../../../infrastructure/docker/docker-compose.yml");

  @Test
  void imagesMatchTheComposeFile() throws IOException {
    String compose = Files.readString(COMPOSE);

    assertThat(compose)
        .contains("image: " + Containers.POSTGRES_IMAGE)
        .contains("image: " + Containers.KAFKA_IMAGE);
  }

  @Test
  void onlyTheLiteralTrueMeansCi() {
    assertThat(DockerAvailableCondition.isCi("true")).isTrue();
    assertThat(DockerAvailableCondition.isCi("TRUE")).isTrue();
    assertThat(DockerAvailableCondition.isCi(null)).isFalse();
    assertThat(DockerAvailableCondition.isCi("1")).isFalse();
  }
}
