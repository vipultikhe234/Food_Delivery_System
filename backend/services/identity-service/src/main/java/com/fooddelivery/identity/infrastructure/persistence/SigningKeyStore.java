package com.fooddelivery.identity.infrastructure.persistence;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Public signing keys published in the JWKS (docs/06 §2.1 {@code signing_keys}). */
@Repository
public class SigningKeyStore {

  private final NamedParameterJdbcTemplate jdbc;

  public SigningKeyStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Adds the key as ACTIVE unless it is already known. */
  public void registerActive(String kid, String publicJwkJson, Instant retireAfter) {
    jdbc.update(
        """
        INSERT INTO signing_keys (kid, public_jwk, status, retire_after)
        VALUES (:kid, CAST(:jwk AS jsonb), 'ACTIVE', :retireAfter)
        ON CONFLICT (kid) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("kid", kid)
            .addValue("jwk", publicJwkJson)
            .addValue(
                "retireAfter",
                retireAfter == null ? null : retireAfter.atOffset(ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE));
  }

  /** Keys that validators must still accept: active or retiring, and not past their retirement. */
  public List<String> publishedKeys() {
    return jdbc.queryForList(
        """
        SELECT public_jwk::text FROM signing_keys
        WHERE status IN ('ACTIVE', 'RETIRING') AND (retire_after IS NULL OR retire_after > now())
        ORDER BY created_at DESC
        """,
        new MapSqlParameterSource(),
        String.class);
  }
}
