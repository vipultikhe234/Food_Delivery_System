package com.fooddelivery.identity.infrastructure.persistence;

import com.fooddelivery.platform.persistence.id.UuidV7;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Role assignments and the grants they carry into access tokens (docs/06 §2.1). */
@Repository
public class RoleStore {

  /** A restaurant or branch scope of a role assignment (docs/09 §2.2, {@code scopes} claim). */
  public record Scope(String type, UUID id) {}

  public record Grants(List<String> roles, List<String> permissions, List<Scope> scopes) {}

  private final NamedParameterJdbcTemplate jdbc;

  public RoleStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Grants a role without scope. The user row must already be flushed. */
  public void assignGlobal(UUID userId, String roleCode, UUID grantedBy) {
    int inserted =
        jdbc.update(
            """
            INSERT INTO user_roles (id, user_id, role_id, scope_type, scope_id, granted_by)
            SELECT :id, :userId, r.id, 'GLOBAL', NULL, :grantedBy FROM roles r WHERE r.code = :code
            """,
            new MapSqlParameterSource()
                .addValue("id", UuidV7.generate())
                .addValue("userId", userId)
                .addValue("code", roleCode)
                .addValue("grantedBy", grantedBy, Types.OTHER));
    if (inserted != 1) {
      throw new IllegalStateException("role " + roleCode + " is not defined");
    }
  }

  public Grants grants(UUID userId) {
    Map<String, Object> params = Map.of("userId", userId);
    List<String> roles =
        jdbc.queryForList(
            """
            SELECT DISTINCT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = :userId ORDER BY r.code
            """,
            params,
            String.class);
    List<String> permissions =
        jdbc.queryForList(
            """
            SELECT DISTINCT p.code FROM user_roles ur
            JOIN role_permissions rp ON rp.role_id = ur.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE ur.user_id = :userId ORDER BY p.code
            """,
            params,
            String.class);
    List<Scope> scopes =
        jdbc.query(
            """
            SELECT DISTINCT scope_type, scope_id FROM user_roles
            WHERE user_id = :userId AND scope_type <> 'GLOBAL' ORDER BY scope_type, scope_id
            """,
            params,
            (rs, row) ->
                new Scope(rs.getString("scope_type"), rs.getObject("scope_id", UUID.class)));
    return new Grants(roles, permissions, scopes);
  }
}
