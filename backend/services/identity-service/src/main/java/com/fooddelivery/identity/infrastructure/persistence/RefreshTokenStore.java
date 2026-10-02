package com.fooddelivery.identity.infrastructure.persistence;

import com.fooddelivery.identity.domain.RevokeReason;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Hashed refresh tokens (docs/06 §2.1). Callers own the transaction. */
@Repository
public class RefreshTokenStore {

  public record NewToken(
      UUID id,
      UUID userId,
      UUID familyId,
      String tokenHash,
      UUID sessionId,
      String deviceInfo,
      String ip,
      Instant issuedAt,
      Instant expiresAt) {}

  public record StoredToken(
      UUID id,
      UUID userId,
      UUID familyId,
      UUID sessionId,
      String deviceInfo,
      Instant expiresAt,
      Instant rotatedAt,
      Instant revokedAt) {}

  private final NamedParameterJdbcTemplate jdbc;

  public RefreshTokenStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(NewToken token) {
    jdbc.update(
        """
        INSERT INTO refresh_tokens
          (id, user_id, family_id, token_hash, session_id, device_info, ip, issued_at, expires_at)
        VALUES
          (:id, :userId, :familyId, :tokenHash, :sessionId, :deviceInfo, CAST(:ip AS inet),
           :issuedAt, :expiresAt)
        """,
        new MapSqlParameterSource()
            .addValue("id", token.id())
            .addValue("userId", token.userId())
            .addValue("familyId", token.familyId())
            .addValue("tokenHash", token.tokenHash())
            .addValue("sessionId", token.sessionId())
            .addValue("deviceInfo", token.deviceInfo(), Types.VARCHAR)
            .addValue("ip", token.ip(), Types.VARCHAR)
            .addValue("issuedAt", utc(token.issuedAt()))
            .addValue("expiresAt", utc(token.expiresAt())));
  }

  /** Locks the row so that two concurrent refreshes with the same token are serialised. */
  public Optional<StoredToken> findForUpdate(String tokenHash) {
    List<StoredToken> rows =
        jdbc.query(
            """
            SELECT id, user_id, family_id, session_id, device_info, expires_at, rotated_at, revoked_at
            FROM refresh_tokens WHERE token_hash = :tokenHash FOR UPDATE
            """,
            new MapSqlParameterSource("tokenHash", tokenHash),
            (rs, row) -> stored(rs));
    return rows.stream().findFirst();
  }

  public void markRotated(UUID id, Instant at) {
    jdbc.update(
        "UPDATE refresh_tokens SET rotated_at = :at WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("at", utc(at)));
  }

  /** Revokes every still-valid token of the family; returns the number of tokens revoked. */
  public int revokeFamily(UUID familyId, Instant at, RevokeReason reason) {
    return jdbc.update(
        """
        UPDATE refresh_tokens SET revoked_at = :at, revoke_reason = :reason
        WHERE family_id = :familyId AND revoked_at IS NULL
        """,
        new MapSqlParameterSource()
            .addValue("familyId", familyId)
            .addValue("at", utc(at))
            .addValue("reason", reason.name()));
  }

  private static StoredToken stored(ResultSet rs) throws SQLException {
    return new StoredToken(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getObject("family_id", UUID.class),
        rs.getObject("session_id", UUID.class),
        rs.getString("device_info"),
        instant(rs, "expires_at"),
        instant(rs, "rotated_at"),
        instant(rs, "revoked_at"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
