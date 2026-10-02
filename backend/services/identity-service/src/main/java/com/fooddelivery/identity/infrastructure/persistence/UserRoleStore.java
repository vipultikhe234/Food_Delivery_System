package com.fooddelivery.identity.infrastructure.persistence;

import com.fooddelivery.platform.persistence.id.UuidV7;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Role assignments of users, with their scope (docs/06 §2.1 {@code user_roles}). */
@Repository
public class UserRoleStore {

  public static final String GLOBAL = "GLOBAL";

  /**
   * @param scopeId null for a GLOBAL assignment
   * @param grantedBy null when granted by the system (self-registration, bootstrap)
   */
  public record Assignment(
      UUID id,
      UUID userId,
      String role,
      String scopeType,
      UUID scopeId,
      UUID grantedBy,
      Instant grantedAt) {}

  private static final String SELECT =
      """
      SELECT ur.id, ur.user_id, r.code AS role, ur.scope_type, ur.scope_id, ur.granted_by,
             ur.created_at
      FROM user_roles ur JOIN roles r ON r.id = ur.role_id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public UserRoleStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Assignment> forUser(UUID userId) {
    return jdbc.query(
        SELECT + " WHERE ur.user_id = :userId ORDER BY ur.created_at, ur.id",
        Map.of("userId", userId),
        UserRoleStore::assignment);
  }

  /** Locks the assignment so concurrent revocations see each other. */
  public Optional<Assignment> lock(UUID userId, UUID assignmentId) {
    return jdbc
        .query(
            SELECT + " WHERE ur.user_id = :userId AND ur.id = :id FOR UPDATE OF ur",
            Map.of("userId", userId, "id", assignmentId),
            UserRoleStore::assignment)
        .stream()
        .findFirst();
  }

  /** The stored assignment and whether this call created it. */
  public record Granted(Assignment assignment, boolean created) {}

  /**
   * Inserts the assignment unless the same role and scope is already held, and returns the stored
   * one either way.
   */
  public Granted insertIfAbsent(
      UUID userId, UUID roleId, String scopeType, UUID scopeId, UUID grantedBy, Instant at) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", UuidV7.generate())
            .addValue("userId", userId)
            .addValue("roleId", roleId)
            .addValue("scopeType", scopeType)
            .addValue("scopeId", scopeId, Types.OTHER)
            .addValue("grantedBy", grantedBy, Types.OTHER)
            .addValue("at", at.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    int inserted =
        jdbc.update(
            """
            INSERT INTO user_roles
                (id, user_id, role_id, scope_type, scope_id, granted_by, created_at, updated_at,
                 created_by, updated_by)
            VALUES (:id, :userId, :roleId, :scopeType, :scopeId, :grantedBy, :at, :at,
                    :grantedBy, :grantedBy)
            ON CONFLICT DO NOTHING
            """,
            params);
    Assignment stored =
        jdbc.queryForObject(
            SELECT
                + """
                 WHERE ur.user_id = :userId AND ur.role_id = :roleId AND ur.scope_type = :scopeType
                   AND ur.scope_id IS NOT DISTINCT FROM CAST(:scopeId AS uuid)
                """,
            params,
            UserRoleStore::assignment);
    return new Granted(stored, inserted == 1);
  }

  public void delete(UUID assignmentId) {
    jdbc.update("DELETE FROM user_roles WHERE id = :id", Map.of("id", assignmentId));
  }

  /** Distinct users holding the role; call with the role row locked. */
  public int holders(UUID roleId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(DISTINCT user_id) FROM user_roles WHERE role_id = :roleId",
            Map.of("roleId", roleId),
            Integer.class);
    return count == null ? 0 : count;
  }

  private static Assignment assignment(ResultSet rs, int row) throws SQLException {
    return new Assignment(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("role"),
        rs.getString("scope_type"),
        rs.getObject("scope_id", UUID.class),
        rs.getObject("granted_by", UUID.class),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
