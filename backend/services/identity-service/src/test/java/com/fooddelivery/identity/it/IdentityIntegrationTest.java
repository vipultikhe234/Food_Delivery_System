package com.fooddelivery.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fooddelivery.platform.testsupport.Containers;
import com.fooddelivery.platform.testsupport.RequiresDocker;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-AUTH-001 AC1–AC5 end to end: the real migrations, PostgreSQL, Kafka and the shared
 * configuration from infrastructure/config-repo.
 */
@RequiresDocker
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "CONFIG_IMPORT=file:"
          + IdentityIntegrationTest.CONFIG_REPO
          + "application.yml,file:"
          + IdentityIntegrationTest.CONFIG_REPO
          + "identity-service.yml",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "MANAGEMENT_PORT=0",
      "JWT_EPHEMERAL_KEY=true",
      "fdp.events.outbox.poll-interval=100ms"
    })
class IdentityIntegrationTest {

  static final String CONFIG_REPO = "../../../infrastructure/config-repo/";
  private static final String PASSWORD = "correct horse battery staple";
  private static final Duration WAIT = Duration.ofSeconds(60);

  @Container static final PostgreSQLContainer DB = Containers.postgres();
  @Container static final KafkaContainer KAFKA = Containers.kafka();

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("DB_URL", DB::getJdbcUrl);
    registry.add("DB_APP_USER", DB::getUsername);
    registry.add("DB_APP_PASSWORD", DB::getPassword);
    registry.add("DB_OWNER_USER", DB::getUsername);
    registry.add("DB_OWNER_PASSWORD", DB::getPassword);
    registry.add("KAFKA_BOOTSTRAP_SERVERS", KAFKA::getBootstrapServers);
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;

  private final HttpClient http = HttpClient.newHttpClient();

  record Response(int status, String contentType, JsonNode body) {
    String code() {
      return body.path("code").asString();
    }
  }

  private Response post(String path, String body) throws Exception {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .header("User-Agent", "identity-it/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private Response get(String path) throws Exception {
    return send(HttpRequest.newBuilder(uri(path)).GET().build());
  }

  private Response send(HttpRequest request) throws Exception {
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    String body = response.body();
    return new Response(
        response.statusCode(),
        response.headers().firstValue("Content-Type").orElse(""),
        body.isEmpty() ? json.createObjectNode() : json.readTree(body));
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static String uniqueEmail() {
    return "asha." + UUID.randomUUID() + "@Example.com";
  }

  private static String uniquePhone() {
    long digits = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L);
    return "+91" + (6_000_000_000L + digits);
  }

  private Response register(String email, String phone) throws Exception {
    return post(
        "/api/v1/auth/register",
        json.writeValueAsString(
            Map.of("fullName", "Asha Rao", "email", email, "phone", phone, "password", PASSWORD)));
  }

  private Response refresh(String token) throws Exception {
    return post("/api/v1/auth/refresh", json.writeValueAsString(Map.of("refreshToken", token)));
  }

  @Test
  void registrationStoresOnlyAnArgon2HashAndGrantsTheCustomerRole(CapturedOutput output)
      throws Exception {
    String email = uniqueEmail();
    Response registered = register(email, uniquePhone());

    assertThat(registered.status()).isEqualTo(201);
    String userId = registered.body().path("userId").asString();
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT email::text AS email, password_hash, status FROM users WHERE id = ?::uuid",
            userId);
    assertThat(row.get("email")).isEqualTo(email.toLowerCase());
    assertThat((String) row.get("password_hash")).startsWith("$argon2id$").doesNotContain(PASSWORD);
    assertThat(row.get("status")).isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForList(
                "SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id"
                    + " WHERE ur.user_id = ?::uuid",
                String.class,
                userId))
        .containsExactly("CUSTOMER");
    assertThat(output.getAll())
        .as("AC2: the password never reaches the logs")
        .doesNotContain(PASSWORD);
  }

  @Test
  void aDuplicateEmailInAnyCaseOrADuplicatePhoneIsRejected() throws Exception {
    String email = uniqueEmail();
    String phone = uniquePhone();
    assertThat(register(email, phone).status()).isEqualTo(201);

    Response sameEmail = register(email.toUpperCase(), uniquePhone());
    Response samePhone = register(uniqueEmail(), phone);

    for (Response conflict : List.of(sameEmail, samePhone)) {
      assertThat(conflict.status()).isEqualTo(409);
      assertThat(conflict.contentType()).startsWith("application/problem+json");
      assertThat(conflict.code()).isEqualTo("USER_ALREADY_EXISTS");
    }
  }

  @Test
  void loginWorksWithEitherIdentifierAndFailuresLookTheSame() throws Exception {
    String email = uniqueEmail();
    String phone = uniquePhone();
    register(email, phone);

    Response byEmail =
        post(
            "/api/v1/auth/login",
            json.writeValueAsString(
                Map.of("identifier", email.toLowerCase(), "password", PASSWORD)));
    Response byPhone =
        post(
            "/api/v1/auth/login",
            json.writeValueAsString(Map.of("identifier", phone, "password", PASSWORD)));
    assertThat(byEmail.status()).isEqualTo(200);
    assertThat(byPhone.status()).isEqualTo(200);
    assertThat(byEmail.body().path("sessionId").asString())
        .isNotEqualTo(byPhone.body().path("sessionId").asString());

    Response wrongPassword =
        post(
            "/api/v1/auth/login",
            json.writeValueAsString(Map.of("identifier", email, "password", PASSWORD + "x")));
    Response unknownUser =
        post(
            "/api/v1/auth/login",
            json.writeValueAsString(Map.of("identifier", uniqueEmail(), "password", PASSWORD)));
    for (Response failure : List.of(wrongPassword, unknownUser)) {
      assertThat(failure.status()).isEqualTo(401);
      assertThat(failure.code()).isEqualTo("INVALID_CREDENTIALS");
      assertThat(failure.body().path("detail").asString())
          .isEqualTo(wrongPassword.body().path("detail").asString());
    }
  }

  @Test
  void refreshRotatesAndReusingARotatedTokenRevokesTheFamily() throws Exception {
    Response registered = register(uniqueEmail(), uniquePhone());
    String first = registered.body().path("refreshToken").asString();

    Response rotated = refresh(first);
    assertThat(rotated.status()).isEqualTo(200);
    String second = rotated.body().path("refreshToken").asString();
    assertThat(second).isNotEqualTo(first);
    assertThat(rotated.body().path("sessionId").asString())
        .isEqualTo(registered.body().path("sessionId").asString());

    Response reuse = refresh(first);
    assertThat(reuse.status()).isEqualTo(401);
    assertThat(reuse.code()).isEqualTo("REFRESH_TOKEN_REUSED");

    Response afterRevocation = refresh(second);
    assertThat(afterRevocation.status()).isEqualTo(401);
    assertThat(afterRevocation.code()).isEqualTo("REFRESH_TOKEN_INVALID");

    assertThat(
            jdbc.queryForList(
                "SELECT revoke_reason FROM refresh_tokens WHERE session_id = ?::uuid",
                String.class,
                registered.body().path("sessionId").asString()))
        .hasSize(2)
        .containsOnly("REUSE_DETECTED");
  }

  @Test
  void accessTokensVerifyAgainstThePublishedJwks() throws Exception {
    Response registered = register(uniqueEmail(), uniquePhone());

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(uri("/.well-known/jwks.json").toString()).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("fooddelivery-identity"),
            new JwtClaimValidator<List<String>>("aud", aud -> aud.contains("fooddelivery-api"))));
    Jwt jwt = decoder.decode(registered.body().path("accessToken").asString());

    assertThat(jwt.getSubject()).isEqualTo(registered.body().path("userId").asString());
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
    assertThat(jwt.getClaimAsString("sid"))
        .isEqualTo(registered.body().path("sessionId").asString());
  }

  @Test
  void userRegisteredIsPublishedWithoutContactDetails() throws Exception {
    String email = uniqueEmail();
    String phone = uniquePhone();
    String userId = register(email, phone).body().path("userId").asString();

    ConsumerRecord<String, String> record = awaitRecord("identity.events.v1", userId);
    JsonNode envelope = json.readTree(record.value());
    assertThat(envelope.path("eventType").asString()).isEqualTo("UserRegistered");
    assertThat(envelope.path("eventVersion").asInt()).isEqualTo(1);
    assertThat(envelope.path("producer").asString()).isEqualTo("identity-service");
    assertThat(envelope.path("actor").path("type").asString()).isEqualTo("USER");
    JsonNode payload = envelope.path("payload");
    assertThat(payload.path("userId").asString()).isEqualTo(userId);
    assertThat(payload.path("fullName").asString()).isEqualTo("Asha Rao");
    assertThat(new ArrayList<>(payload.propertyNames()))
        .containsExactlyInAnyOrderElementsOf(schemaProperties());
    assertThat(record.value()).doesNotContain(email.toLowerCase(), phone);
  }

  @Test
  void undefinedEndpointsAreDeniedWithAProblemDocument() throws Exception {
    Response denied = get("/api/v1/auth/sessions");

    assertThat(denied.status()).isEqualTo(401);
    assertThat(denied.contentType()).startsWith("application/problem+json");
    assertThat(denied.code()).isEqualTo("UNAUTHENTICATED");
  }

  private List<String> schemaProperties() throws Exception {
    try (InputStream in =
        getClass()
            .getResourceAsStream(
                "/event-contracts/identity.events.v1/UserRegistered.v1.schema.json")) {
      assertThat(in).as("UserRegistered schema on the classpath").isNotNull();
      return new ArrayList<>(json.readTree(in).path("properties").propertyNames());
    }
  }

  private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "identity-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    AtomicReference<ConsumerRecord<String, String>> found = new AtomicReference<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of(topic));
      await()
          .atMost(WAIT)
          .until(
              () -> {
                for (ConsumerRecord<String, String> record :
                    consumer.poll(Duration.ofMillis(500))) {
                  if (key.equals(record.key())) {
                    found.set(record);
                  }
                }
                return found.get() != null;
              });
    }
    return found.get();
  }
}
