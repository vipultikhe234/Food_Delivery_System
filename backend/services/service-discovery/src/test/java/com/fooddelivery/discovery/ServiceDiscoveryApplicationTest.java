package com.fooddelivery.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
class ServiceDiscoveryApplicationTest {

  @LocalServerPort int port;

  @LocalManagementPort int managementPort;

  @Value("${eureka.server.enable-self-preservation}")
  boolean selfPreservation;

  @Test
  void exposesTheRegistryAndProbes() throws Exception {
    assertThat(get("http://localhost:" + port + "/eureka/apps", "application/json"))
        .contains("applications");
    assertThat(get("http://localhost:" + managementPort + "/actuator/health/liveness", "*/*"))
        .contains("UP");
    assertThat(get("http://localhost:" + managementPort + "/actuator/health/readiness", "*/*"))
        .contains("UP");
    assertThat(selfPreservation).isFalse();
  }

  private static String get(String url, String accept) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create(url)).header("Accept", accept).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      return response.body();
    }
  }
}
