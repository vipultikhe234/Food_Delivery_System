package com.fooddelivery.platform.persistence.idempotency;

import com.fooddelivery.platform.web.error.ApiException;
import com.fooddelivery.platform.web.error.CommonErrorCode;
import com.fooddelivery.platform.web.error.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a mutating endpoint safe to retry (REQ-PLAT-006 AC2–AC5, docs/07-api-design.md §5).
 *
 * <pre>{@code
 * @PostMapping("/api/v1/orders")
 * ResponseEntity<?> place(@Valid @RequestBody PlaceOrder body, HttpServletRequest request) {
 *   return idempotency.execute(request, body, () -> ResponseEntity.status(201).body(orders.place(body)));
 * }
 * }</pre>
 *
 * <p>The stored response is written in the same transaction as the business change, so either both
 * commit or neither does. If the action fails, the key is released and the client may retry.
 */
public class IdempotencyService {

  public static final String HEADER = "Idempotency-Key";
  public static final String REPLAYED_HEADER = "Idempotent-Replayed";
  private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
  private static final List<String> REPLAYED_RESPONSE_HEADERS =
      List.of(HttpHeaders.LOCATION, HttpHeaders.ETAG);

  private static final String CLAIM =
      """
      INSERT INTO idempotency_keys
          (user_id, idem_key, endpoint, request_hash, status, created_at, expires_at)
      VALUES (:user, :key, :endpoint, :hash, 'IN_PROGRESS', now(), now() + make_interval(secs => :ttl))
      ON CONFLICT (user_id, endpoint, idem_key) DO UPDATE
         SET request_hash = EXCLUDED.request_hash, status = 'IN_PROGRESS',
             response_status = NULL, response_body = NULL, response_headers = NULL,
             created_at = now(), expires_at = EXCLUDED.expires_at
       WHERE idempotency_keys.expires_at <= now()
          OR (idempotency_keys.status = 'IN_PROGRESS'
              AND idempotency_keys.request_hash = EXCLUDED.request_hash
              AND idempotency_keys.created_at <= now() - make_interval(secs => :stale))
      RETURNING 1
      """;

  private static final String FIND =
      """
      SELECT request_hash, status, response_status, response_body::text AS body,
             response_headers::text AS headers
        FROM idempotency_keys
       WHERE user_id = :user AND endpoint = :endpoint AND idem_key = :key
      """;

  private static final String COMPLETE =
      """
      UPDATE idempotency_keys
         SET status = 'COMPLETED', response_status = :status,
             response_body = CAST(:body AS jsonb), response_headers = CAST(:headers AS jsonb)
       WHERE user_id = :user AND endpoint = :endpoint AND idem_key = :key
      """;

  private static final String RELEASE =
      """
      DELETE FROM idempotency_keys
       WHERE user_id = :user AND endpoint = :endpoint AND idem_key = :key AND status = 'IN_PROGRESS'
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate business;
  private final TransactionTemplate separate;
  private final JsonMapper json;
  private final JsonMapper canonical;
  private final IdempotencyProperties properties;

  public IdempotencyService(
      NamedParameterJdbcTemplate jdbc,
      PlatformTransactionManager transactions,
      JsonMapper json,
      IdempotencyProperties properties) {
    this.jdbc = jdbc;
    this.business = new TransactionTemplate(transactions);
    this.separate = new TransactionTemplate(transactions);
    this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.json = json;
    this.canonical =
        json.rebuild()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    this.properties = properties;
  }

  public ResponseEntity<?> execute(
      HttpServletRequest request, Object body, Supplier<? extends ResponseEntity<?>> action) {
    MapSqlParameterSource key =
        new MapSqlParameterSource()
            .addValue("user", userId(request))
            .addValue("key", idempotencyKey(request))
            .addValue("endpoint", endpoint(request));
    String hash = hash(request, body);

    if (!claim(key, hash)) {
      return existing(key, hash);
    }
    try {
      return business.execute(
          status -> {
            ResponseEntity<?> response = action.get();
            complete(key, response);
            return response;
          });
    } catch (RuntimeException | Error e) {
      separate.executeWithoutResult(status -> jdbc.update(RELEASE, key));
      throw e;
    }
  }

  private boolean claim(MapSqlParameterSource key, String hash) {
    MapSqlParameterSource params =
        new MapSqlParameterSource(key.getValues())
            .addValue("hash", hash)
            .addValue("ttl", properties.ttl().toSeconds())
            .addValue("stale", properties.inProgressTimeout().toSeconds());
    Boolean claimed =
        separate.execute(status -> !jdbc.queryForList(CLAIM, params, Integer.class).isEmpty());
    return Boolean.TRUE.equals(claimed);
  }

  private ResponseEntity<?> existing(MapSqlParameterSource key, String hash) {
    Map<String, Object> row =
        jdbc.queryForList(FIND, key).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(
                        CommonErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "A request with this Idempotency-Key is being processed.",
                        List.of(),
                        Map.of(HttpHeaders.RETRY_AFTER, "1"),
                        null));
    if (!hash.equals(row.get("request_hash"))) {
      throw new ApiException(
          CommonErrorCode.IDEMPOTENCY_KEY_REUSED,
          "This Idempotency-Key was already used with a different request.");
    }
    if (!"COMPLETED".equals(row.get("status"))) {
      throw new ApiException(
          CommonErrorCode.IDEMPOTENCY_IN_PROGRESS,
          "A request with this Idempotency-Key is being processed.",
          List.of(),
          Map.of(HttpHeaders.RETRY_AFTER, "1"),
          null);
    }
    return replay(row);
  }

  private ResponseEntity<?> replay(Map<String, Object> row) {
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(((Number) row.get("response_status")).intValue())
            .header(REPLAYED_HEADER, "true");
    String headers = (String) row.get("headers");
    if (headers != null) {
      json.readTree(headers)
          .properties()
          .forEach(e -> response.header(e.getKey(), e.getValue().asString()));
    }
    String body = (String) row.get("body");
    if (body == null) {
      return response.build();
    }
    JsonNode tree = json.readTree(body);
    return response.contentType(MediaType.APPLICATION_JSON).body(tree);
  }

  private void complete(MapSqlParameterSource key, ResponseEntity<?> response) {
    Map<String, String> headers = new LinkedHashMap<>();
    for (String name : REPLAYED_RESPONSE_HEADERS) {
      Optional.ofNullable(response.getHeaders().getFirst(name))
          .ifPresent(v -> headers.put(name, v));
    }
    jdbc.update(
        COMPLETE,
        new MapSqlParameterSource(key.getValues())
            .addValue("status", response.getStatusCode().value())
            .addValue(
                "body",
                response.getBody() == null ? null : json.writeValueAsString(response.getBody()))
            .addValue("headers", headers.isEmpty() ? null : json.writeValueAsString(headers)));
  }

  private static UUID userId(HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    if (principal != null) {
      try {
        return UUID.fromString(principal.getName());
      } catch (IllegalArgumentException e) {
        // fall through: idempotency keys are scoped per authenticated user
      }
    }
    throw new ApiException(CommonErrorCode.UNAUTHENTICATED, "A valid access token is required.");
  }

  private static String idempotencyKey(HttpServletRequest request) {
    String key = request.getHeader(HEADER);
    if (key == null || key.isBlank()) {
      throw invalidKey("REQUIRED", "header is required", "The Idempotency-Key header is required.");
    }
    if (!KEY.matcher(key).matches()) {
      throw invalidKey(
          "INVALID_VALUE",
          "must be 1-128 characters: letters, digits, '.', '_', ':' or '-'",
          "The Idempotency-Key header is invalid.");
    }
    return key;
  }

  private static ApiException invalidKey(String code, String message, String detail) {
    return new ApiException(
        CommonErrorCode.VALIDATION_FAILED,
        detail,
        List.of(new FieldViolation(HEADER, code, message)),
        Map.of(),
        null);
  }

  private static String endpoint(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String path = pattern != null ? pattern.toString() : request.getRequestURI();
    String endpoint = request.getMethod() + " " + path;
    return endpoint.length() <= 128 ? endpoint : endpoint.substring(0, 128);
  }

  /**
   * Same key on another resource (e.g. another order id) must not replay: the URI is hashed too.
   */
  private String hash(HttpServletRequest request, Object body) {
    String canonicalBody = body == null ? "" : canonical.writeValueAsString(body);
    String material = request.getMethod() + " " + request.getRequestURI() + "\n" + canonicalBody;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
