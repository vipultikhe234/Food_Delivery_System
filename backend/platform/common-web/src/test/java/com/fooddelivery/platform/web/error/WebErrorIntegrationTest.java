package com.fooddelivery.platform.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.application.name=web-error-test")
class WebErrorIntegrationTest {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final String INTERNAL_TEXT = "SELECT card_number FROM payments_internal";

  @LocalServerPort int port;

  @AfterAll
  static void closeClient() {
    CLIENT.close();
  }

  @Test
  void applicationErrorsUseTheStandardFormat() throws Exception {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(uri("/test/not-cancellable")).header("X-Correlation-Id", "c-1"));

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    assertThat(response.headers().firstValue("Retry-After")).hasValue("5");
    DocumentContext body = JsonPath.parse(response.body());
    assertThat(body.read("$.type", String.class))
        .isEqualTo("https://docs.fooddelivery.example/errors/ORDER_NOT_CANCELLABLE");
    assertThat(body.read("$.title", String.class)).isEqualTo("Order not cancellable");
    assertThat(body.read("$.status", Integer.class)).isEqualTo(409);
    assertThat(body.read("$.code", String.class)).isEqualTo("ORDER_NOT_CANCELLABLE");
    assertThat(body.read("$.detail", String.class)).isEqualTo("Order is already out for delivery.");
    assertThat(body.read("$.instance", String.class)).isEqualTo("/test/not-cancellable");
    assertThat(body.read("$.correlationId", String.class)).isEqualTo("c-1");
    assertThat(Instant.parse(body.read("$.timestamp", String.class))).isNotNull();
    assertThat(body.read("$", Map.class)).doesNotContainKey("errors");
  }

  @Test
  void validationFailuresListEveryInvalidField() throws Exception {
    HttpResponse<String> response = post("/test/items", "{\"name\":\" \",\"quantity\":51}");

    assertThat(response.statusCode()).isEqualTo(400);
    DocumentContext body = JsonPath.parse(response.body());
    assertThat(body.read("$.code", String.class)).isEqualTo("VALIDATION_FAILED");
    List<Map<String, String>> errors = body.read("$.errors");
    assertThat(errors)
        .extracting(e -> e.get("field") + ":" + e.get("code"))
        .containsExactlyInAnyOrder("name:NOT_BLANK", "quantity:MAX");
    assertThat(errors).allSatisfy(e -> assertThat(e.get("message")).isNotBlank());
  }

  @Test
  void unknownPropertiesAreRejected() throws Exception {
    HttpResponse<String> response =
        post("/test/items", "{\"name\":\"Paneer\",\"quantity\":1,\"price\":\"0.01\"}");

    assertThat(response.statusCode()).isEqualTo(400);
    DocumentContext body = JsonPath.parse(response.body());
    assertThat(body.read("$.errors[0].field", String.class)).isEqualTo("price");
    assertThat(body.read("$.errors[0].code", String.class)).isEqualTo("UNKNOWN_PROPERTY");
  }

  @Test
  void malformedBodiesAndBadParametersDoNotLeakParserDetails() throws Exception {
    HttpResponse<String> malformed = post("/test/items", "{\"name\": ");
    HttpResponse<String> wrongType = post("/test/items", "{\"name\":\"x\",\"quantity\":\"lots\"}");
    HttpResponse<String> missing = send(HttpRequest.newBuilder(uri("/test/search")));
    HttpResponse<String> mismatch = send(HttpRequest.newBuilder(uri("/test/search?limit=abc")));

    assertThat(List.of(malformed, wrongType, missing, mismatch))
        .allSatisfy(
            r -> {
              assertThat(r.statusCode()).isEqualTo(400);
              assertThat(r.body())
                  .doesNotContainIgnoringCase("jackson")
                  .doesNotContain("Exception");
            });
    assertThat(JsonPath.parse(wrongType.body()).read("$.errors[0].field", String.class))
        .isEqualTo("quantity");
    assertThat(JsonPath.parse(missing.body()).read("$.errors[0].code", String.class))
        .isEqualTo("REQUIRED");
    assertThat(JsonPath.parse(mismatch.body()).read("$.errors[0].field", String.class))
        .isEqualTo("limit");
  }

  @Test
  void unexpectedFailuresReturnAGenericMessageWithoutInternals() throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/test/boom")));

    assertThat(response.statusCode()).isEqualTo(500);
    DocumentContext body = JsonPath.parse(response.body());
    assertThat(body.read("$.code", String.class)).isEqualTo("INTERNAL_ERROR");
    assertThat(body.read("$.detail", String.class)).isEqualTo("An unexpected error occurred");
    assertThat(response.body())
        .doesNotContain("SELECT", "payments_internal", "IllegalStateException", "at com.");
  }

  @Test
  void staleVersionReturnsConcurrentModification() throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/test/stale")));

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(JsonPath.parse(response.body()).read("$.code", String.class))
        .isEqualTo("CONCURRENT_MODIFICATION");
    assertThat(response.body()).doesNotContain("Order#", "version 3");
  }

  @Test
  void methodSecurityDenialReturnsForbidden() throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/test/denied")));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(JsonPath.parse(response.body()).read("$.code", String.class)).isEqualTo("FORBIDDEN");
  }

  @Test
  void frameworkErrorsKeepTheirStatusWithFixedTexts() throws Exception {
    HttpResponse<String> notFound =
        send(HttpRequest.newBuilder(uri("/api/v1/nothing-here")).header("Accept", "text/html"));
    HttpResponse<String> wrongMethod = send(HttpRequest.newBuilder(uri("/test/boom")).DELETE());

    assertThat(notFound.statusCode()).isEqualTo(404);
    assertThat(notFound.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    assertThat(JsonPath.parse(notFound.body()).read("$.code", String.class)).isEqualTo("NOT_FOUND");
    assertThat(notFound.body()).doesNotContain("static resource");
    assertThat(wrongMethod.statusCode()).isEqualTo(405);
    assertThat(JsonPath.parse(wrongMethod.body()).read("$.detail", String.class))
        .isEqualTo("The HTTP method is not supported for this resource.");
  }

  @Test
  void errorsRaisedInFiltersUseTheSameFormat() throws Exception {
    HttpResponse<String> crash = send(HttpRequest.newBuilder(uri("/filtered/crash")));
    HttpResponse<String> rejected = send(HttpRequest.newBuilder(uri("/filtered/rejected")));

    assertThat(crash.statusCode()).isEqualTo(500);
    assertThat(crash.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    DocumentContext crashBody = JsonPath.parse(crash.body());
    assertThat(crashBody.read("$.code", String.class)).isEqualTo("INTERNAL_ERROR");
    assertThat(crashBody.read("$.instance", String.class)).isEqualTo("/filtered/crash");
    assertThat(crash.body()).doesNotContain(INTERNAL_TEXT);
    assertThat(rejected.statusCode()).isEqualTo(429);
    assertThat(JsonPath.parse(rejected.body()).read("$.code", String.class))
        .isEqualTo("RATE_LIMITED");
  }

  private HttpResponse<String> post(String path, String json)
      throws IOException, InterruptedException {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)));
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  enum TestErrorCode implements ErrorCode {
    ORDER_NOT_CANCELLABLE;

    @Override
    public String code() {
      return name();
    }

    @Override
    public HttpStatus status() {
      return HttpStatus.CONFLICT;
    }

    @Override
    public String title() {
      return "Order not cancellable";
    }
  }

  record ItemRequest(@NotBlank String name, @Max(50) int quantity) {}

  @RestController
  static class TestController {

    @GetMapping("/test/not-cancellable")
    void notCancellable() {
      throw new ApiException(
          TestErrorCode.ORDER_NOT_CANCELLABLE,
          "Order is already out for delivery.",
          List.of(),
          Map.of("Retry-After", "5"),
          null);
    }

    @PostMapping("/test/items")
    ItemRequest create(@Valid @RequestBody ItemRequest item) {
      return item;
    }

    @GetMapping("/test/search")
    String search(@RequestParam int limit) {
      return "ok " + limit;
    }

    @GetMapping("/test/boom")
    void boom() {
      throw new IllegalStateException(INTERNAL_TEXT);
    }

    @GetMapping("/test/stale")
    void stale() {
      throw new OptimisticLockingFailureException("Order#42 version 3 is stale");
    }

    @GetMapping("/test/denied")
    void denied() {
      throw new AccessDeniedException("missing ORDER_CANCEL");
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    TestController testController() {
      return new TestController();
    }

    @Bean
    FilterRegistrationBean<Filter> failingFilter() {
      Filter filter =
          (request, response, chain) -> {
            String uri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
            if (uri.endsWith("/crash")) {
              throw new IllegalStateException(INTERNAL_TEXT);
            }
            throw new ApiException(CommonErrorCode.RATE_LIMITED, "Too many requests.");
          };
      FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
      registration.addUrlPatterns("/filtered/*");
      return registration;
    }
  }
}
