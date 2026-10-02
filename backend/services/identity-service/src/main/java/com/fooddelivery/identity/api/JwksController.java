package com.fooddelivery.identity.api;

import com.fooddelivery.identity.infrastructure.persistence.SigningKeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Public keys for local token validation by the gateway and services (REQ-AUTH-001 AC5). Validators
 * cache the set and refetch on an unknown {@code kid}, so a rotated key is picked up without a call
 * per request.
 */
@RestController
class JwksController {

  static final Duration MAX_AGE = Duration.ofMinutes(5);

  private final SigningKeyStore keys;
  private final JsonMapper json;

  JwksController(SigningKeyStore keys, JsonMapper json) {
    this.keys = keys;
    this.json = json;
  }

  @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, List<JsonNode>>> jwks() {
    List<JsonNode> published = keys.publishedKeys().stream().map(json::readTree).toList();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
        .body(Map.of("keys", published));
  }
}
