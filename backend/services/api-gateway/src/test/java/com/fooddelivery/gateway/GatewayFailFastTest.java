package com.fooddelivery.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.client.ConfigClientFailFastException;

/** The gateway must refuse to start without its central configuration (REQ-PLAT-003 AC4). */
class GatewayFailFastTest {

  @Test
  void failsWhenConfigServerIsUnreachable() {
    assertThatThrownBy(
            () ->
                start(
                    "--CONFIG_SERVER_URL=http://127.0.0.1:1",
                    "--spring.cloud.config.request-connect-timeout=500"))
        .isInstanceOf(ConfigClientFailFastException.class);
  }

  @Test
  void failsWhenCorsOriginsAreMissing() {
    assertThatThrownBy(
            () ->
                start(
                    "--CONFIG_IMPORT=file:"
                        + GatewayIntegrationTest.CONFIG_REPO
                        + "application.yml",
                    "--spring.cloud.config.enabled=false"))
        .hasStackTraceContaining("fdp.gateway");
  }

  private static void start(String... args) {
    new SpringApplicationBuilder(ApiGatewayApplication.class)
        .run(
            concat(
                args,
                "--server.port=0",
                "--MANAGEMENT_PORT=0",
                "--eureka.client.enabled=false",
                "--fdp.logging.format=plain"))
        .close();
  }

  private static String[] concat(String[] first, String... second) {
    String[] all = new String[first.length + second.length];
    System.arraycopy(first, 0, all, 0, first.length);
    System.arraycopy(second, 0, all, first.length, second.length);
    return all;
  }
}
