package com.fooddelivery.configserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class ConfigServerApplicationTest {

  private static final String USER = "config-client";
  private static final String PASSWORD = "test-only-password";

  @TempDir Path repo;

  @Test
  void servesProfileSpecificConfigurationToAuthenticatedClients() throws Exception {
    Files.writeString(repo.resolve("application.yml"), "fdp:\n  greeting: shared\n");
    Files.writeString(repo.resolve("demo-service.yml"), "fdp:\n  timeout: 5s\n");
    Files.writeString(repo.resolve("demo-service-staging.yml"), "fdp:\n  timeout: 2s\n");

    try (ConfigurableApplicationContext context = start(repoLocation(), true)) {
      int port = ((WebServerApplicationContext) context).getWebServer().getPort();

      HttpResponse<String> response =
          get("http://localhost:" + port + "/demo-service/staging", basic(USER, PASSWORD));

      assertThat(response.statusCode()).isEqualTo(200);
      List<String> timeouts =
          JsonPath.read(response.body(), "$.propertySources[*].source['fdp.timeout']");
      assertThat(timeouts).first().isEqualTo("2s");
      assertThat(
              JsonPath.<List<String>>read(
                  response.body(), "$.propertySources[*].source['fdp.greeting']"))
          .containsExactly("shared");
    }
  }

  @Test
  void rejectsMissingOrWrongCredentialsButKeepsProbesOpen() throws Exception {
    try (ConfigurableApplicationContext context = start(repoLocation(), true)) {
      int port = ((WebServerApplicationContext) context).getWebServer().getPort();
      int managementPort =
          Integer.parseInt(context.getEnvironment().getProperty("local.management.port"));

      assertThat(get("http://localhost:" + port + "/demo-service/default", null).statusCode())
          .isEqualTo(401);
      assertThat(
              get("http://localhost:" + port + "/demo-service/default", basic(USER, "wrong"))
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              get("http://localhost:" + managementPort + "/actuator/health/readiness", null)
                  .statusCode())
          .isEqualTo(200);
    }
  }

  @Test
  void stateChangingRequestsWithoutACsrfTokenAreRejectedEvenWithValidCredentials()
      throws Exception {
    try (ConfigurableApplicationContext context = start(repoLocation(), true)) {
      int port = ((WebServerApplicationContext) context).getWebServer().getPort();

      HttpResponse<String> response;
      try (HttpClient client = HttpClient.newHttpClient()) {
        response =
            client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/encrypt"))
                    .header("Authorization", basic(USER, PASSWORD))
                    .POST(HttpRequest.BodyPublishers.ofString("value"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
      }

      assertThat(response.statusCode()).isEqualTo(403);
    }
  }

  @Test
  void failsFastWhenRequiredSettingsAreMissing() {
    assertThatThrownBy(() -> start("", true).close())
        .hasStackTraceContaining("must be set (CONFIG_REPO_LOCATION)");
    assertThatThrownBy(
            () -> start("file:" + repo.resolve("absent").toAbsolutePath() + "/", true).close())
        .hasStackTraceContaining("Config repository directory not found");
    assertThatThrownBy(() -> start(repoLocation(), false).close())
        .hasStackTraceContaining("Could not resolve placeholder 'CONFIG_SERVER_");
  }

  private String repoLocation() {
    return "file:" + repo.toAbsolutePath() + "/";
  }

  private static ConfigurableApplicationContext start(String location, boolean credentials) {
    List<String> args =
        new ArrayList<>(
            List.of(
                "--server.port=0",
                "--management.server.port=0",
                "--spring.profiles.active=native",
                "--spring.cloud.config.server.native.search-locations=" + location));
    if (credentials) {
      args.add("--CONFIG_SERVER_USER=" + USER);
      args.add("--CONFIG_SERVER_PASSWORD=" + PASSWORD);
    }
    return new SpringApplicationBuilder(ConfigServerApplication.class)
        .run(args.toArray(String[]::new));
  }

  private static String basic(String user, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> get(String url, String authorization)
      throws IOException, InterruptedException {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
      if (authorization != null) {
        request.header("Authorization", authorization);
      }
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
