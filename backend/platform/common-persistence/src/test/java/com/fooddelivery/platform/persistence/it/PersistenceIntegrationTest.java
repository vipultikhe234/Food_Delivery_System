package com.fooddelivery.platform.persistence.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.platform.persistence.idempotency.IdempotencyKeyPurger;
import com.fooddelivery.platform.persistence.money.Money;
import com.fooddelivery.platform.testsupport.Containers;
import com.fooddelivery.platform.testsupport.RequiresDocker;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** REQ-PLAT-008 AC3–AC5 and REQ-PLAT-006 AC2–AC5 against PostgreSQL (docs/06, docs/07 §5). */
@RequiresDocker
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.application.name=persistence-test",
      "fdp.persistence.idempotency.enabled=true"
    })
class PersistenceIntegrationTest {

  @Container static final PostgreSQLContainer DB = Containers.postgres();

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DB::getJdbcUrl);
    registry.add("spring.datasource.username", DB::getUsername);
    registry.add("spring.datasource.password", DB::getPassword);
  }

  @LocalServerPort int port;
  @Autowired WidgetRepository widgets;
  @Autowired JdbcTemplate jdbc;
  @Autowired PersistenceTestApplication.Behaviour behaviour;
  @Autowired IdempotencyKeyPurger purger;

  @AfterAll
  static void closeClient() {
    CLIENT.close();
  }

  @AfterEach
  void reset() {
    SecurityContextHolder.clearContext();
    behaviour.executions.set(0);
    behaviour.failNext.set(false);
    behaviour.entered = new CountDownLatch(0);
    behaviour.release = new CountDownLatch(0);
  }

  @Test
  void baseColumnsAreFilledOnInsertAndUpdate() {
    UUID user = UUID.randomUUID();
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(user.toString(), null, List.of()));
    Instant before = Instant.now().minusSeconds(1);

    Widget saved = widgets.save(new Widget("audited", Money.inr("249.5")));

    assertThat(saved.getId().version()).isEqualTo(7);
    assertThat(saved.getCreatedAt()).isAfter(before);
    assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    assertThat(saved.getCreatedBy()).isEqualTo(user);
    assertThat(saved.getVersion()).isZero();

    Widget loaded = widgets.findById(saved.getId()).orElseThrow();
    loaded.rename("audited-again");
    Widget updated = widgets.save(loaded);

    assertThat(updated.getVersion()).isEqualTo(1);
    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
    assertThat(updated.getUpdatedBy()).isEqualTo(user);
    assertThat(widgets.findById(saved.getId()).orElseThrow().getPrice())
        .isEqualTo(Money.inr("249.50"));
  }

  @Test
  void moneyIsStoredAsNumeric12Scale2PlusChar3() {
    List<String> types =
        jdbc.queryForList(
            """
            SELECT column_name || ':' || data_type || ':'
                   || coalesce(numeric_precision::text, character_maximum_length::text)
                   || coalesce(':' || numeric_scale::text, '')
              FROM information_schema.columns
             WHERE table_name = 'widgets' AND column_name IN ('amount', 'currency')
             ORDER BY column_name
            """,
            String.class);

    assertThat(types).containsExactly("amount:numeric:12:2", "currency:character:3");
  }

  @Test
  void aStaleVersionIsRejectedWith409() throws Exception {
    Widget widget = widgets.save(new Widget("contended", Money.inr("10")));

    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(uri("/widgets/" + widget.getId() + "/conflicting-renames"))
                .PUT(HttpRequest.BodyPublishers.noBody()));

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(JsonPath.<String>read(response.body(), "$.code"))
        .isEqualTo("CONCURRENT_MODIFICATION");
    assertThat(widgets.findById(widget.getId()).orElseThrow().getName()).isEqualTo("first");
  }

  @Test
  void platformMigrationsKeepTheirOwnHistory() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);

    assertThat(tables)
        .contains(
            "flyway_schema_history",
            "fdp_idempotency_schema_history",
            "idempotency_keys",
            "widgets");
  }

  @Test
  void aKeyIsRequired() throws Exception {
    HttpResponse<String> response =
        create(UUID.randomUUID(), null, "{\"name\":\"a\",\"price\":\"1\"}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field"))
        .isEqualTo("Idempotency-Key");
  }

  @Test
  void aRetryReplaysTheStoredResponseWithoutRunningTheActionAgain() throws Exception {
    UUID user = UUID.randomUUID();
    String body = "{\"name\":\"replayed\",\"price\":\"99\"}";

    HttpResponse<String> first = create(user, "key-replay", body);
    HttpResponse<String> retry = create(user, "key-replay", body.replace(",", " , "));

    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
    assertThat(retry.statusCode()).isEqualTo(201);
    assertThat(retry.headers().firstValue("Idempotent-Replayed")).hasValue("true");
    assertThat(retry.headers().firstValue("Location"))
        .isEqualTo(first.headers().firstValue("Location"));
    assertThat(retry.headers().firstValue("ETag")).isEqualTo(first.headers().firstValue("ETag"));
    assertThat(JsonPath.<String>read(retry.body(), "$.id"))
        .isEqualTo(JsonPath.<String>read(first.body(), "$.id"));
    assertThat(behaviour.executions).hasValue(1);
    assertThat(widgets.countByName("replayed")).isEqualTo(1);
  }

  @Test
  void reusingAKeyForADifferentRequestIs422() throws Exception {
    UUID user = UUID.randomUUID();
    create(user, "key-reused", "{\"name\":\"one\",\"price\":\"1\"}");

    HttpResponse<String> response =
        create(user, "key-reused", "{\"name\":\"two\",\"price\":\"1\"}");

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(JsonPath.<String>read(response.body(), "$.code"))
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(widgets.countByName("two")).isZero();
  }

  @Test
  void keysAreScopedPerUser() throws Exception {
    String body = "{\"name\":\"scoped\",\"price\":\"5\"}";

    assertThat(create(UUID.randomUUID(), "shared-key", body).statusCode()).isEqualTo(201);
    HttpResponse<String> other = create(UUID.randomUUID(), "shared-key", body);

    assertThat(other.statusCode()).isEqualTo(201);
    assertThat(other.headers().firstValue("Idempotent-Replayed")).isEmpty();
    assertThat(behaviour.executions).hasValue(2);
  }

  @Test
  void aConcurrentDuplicateGets409WithRetryAfter() throws Exception {
    UUID user = UUID.randomUUID();
    String body = "{\"name\":\"concurrent\",\"price\":\"7\"}";
    behaviour.entered = new CountDownLatch(1);
    behaviour.release = new CountDownLatch(1);

    CompletableFuture<HttpResponse<String>> first =
        CLIENT.sendAsync(
            createRequest(user, "key-concurrent", body), HttpResponse.BodyHandlers.ofString());
    assertThat(behaviour.entered.await(10, TimeUnit.SECONDS)).isTrue();
    HttpResponse<String> duplicate = create(user, "key-concurrent", body);
    behaviour.release.countDown();

    assertThat(duplicate.statusCode()).isEqualTo(409);
    assertThat(duplicate.headers().firstValue("Retry-After")).hasValue("1");
    assertThat(JsonPath.<String>read(duplicate.body(), "$.code"))
        .isEqualTo("IDEMPOTENCY_IN_PROGRESS");
    assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
    assertThat(behaviour.executions).hasValue(1);
  }

  @Test
  void aFailedActionRollsBackAndReleasesTheKey() throws Exception {
    UUID user = UUID.randomUUID();
    String body = "{\"name\":\"rolled-back\",\"price\":\"3\"}";
    behaviour.failNext.set(true);

    HttpResponse<String> failed = create(user, "key-failure", body);

    assertThat(failed.statusCode()).isEqualTo(500);
    assertThat(widgets.countByName("rolled-back")).isZero();
    assertThat(create(user, "key-failure", body).statusCode()).isEqualTo(201);
    assertThat(widgets.countByName("rolled-back")).isEqualTo(1);
  }

  @Test
  void expiredKeysCanBeReusedAndArePurged() throws Exception {
    UUID user = UUID.randomUUID();
    create(user, "key-expiry", "{\"name\":\"before-expiry\",\"price\":\"2\"}");
    expire("key-expiry");

    HttpResponse<String> reused =
        create(user, "key-expiry", "{\"name\":\"after-expiry\",\"price\":\"2\"}");

    assertThat(reused.statusCode()).isEqualTo(201);
    assertThat(reused.headers().firstValue("Idempotent-Replayed")).isEmpty();
    expire("key-expiry");
    assertThat(purger.purge()).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM idempotency_keys WHERE idem_key = 'key-expiry'", Long.class))
        .isZero();
  }

  private void expire(String key) {
    jdbc.update(
        "UPDATE idempotency_keys SET expires_at = now() - interval '1 second' WHERE idem_key = ?",
        key);
  }

  private HttpResponse<String> create(UUID user, String key, String json) throws Exception {
    return CLIENT.send(createRequest(user, key, json), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest createRequest(UUID user, String key, String json) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri("/widgets"))
            .header("Content-Type", "application/json")
            .header("X-Test-User", user.toString())
            .POST(HttpRequest.BodyPublishers.ofString(json));
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return request.build();
  }

  private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
