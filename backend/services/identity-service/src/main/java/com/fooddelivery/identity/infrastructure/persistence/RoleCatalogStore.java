package com.fooddelivery.identity.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Roles, permissions and the role-to-permission mapping, kept as data (REQ-AUTH-003 AC2). */
@Repository
public class RoleCatalogStore {

  public record Permission(String code, String category, String description) {}

  /**
   * @param scopeTypes GLOBAL, RESTAURANT and/or BRANCH: how the role may be assigned
   */
  public record Role(
      UUID id, String code, String description, Set<String> scopeTypes, List<String> permissions) {}

  private final NamedParameterJdbcTemplate jdbc;

  public RoleCatalogStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Permission> permissions() {
    return jdbc.query(
        "SELECT code, category, description FROM permissions ORDER BY category, code",
        (rs, row) ->
            new Permission(
                rs.getString("code"), rs.getString("category"), rs.getString("description")));
  }

  public List<Role> roles() {
    Map<UUID, List<String>> permissions = permissionsByRole(null);
    return jdbc.query(
        "SELECT id, code, description, scope_types FROM roles ORDER BY code",
        (rs, row) -> role(rs, permissions));
  }

  public Optional<Role> find(String code) {
    List<Role> found =
        jdbc.query(
            "SELECT id, code, description, scope_types FROM roles WHERE code = :code",
            Map.of("code", code),
            (rs, row) -> role(rs, Map.of()));
    return found.stream()
        .findFirst()
        .map(
            role ->
                new Role(
                    role.id(),
                    role.code(),
                    role.description(),
                    role.scopeTypes(),
                    List.copyOf(permissionsByRole(role.id()).getOrDefault(role.id(), List.of()))));
  }

  /** Locks the role row, which serialises changes to its permissions and to its last holder. */
  public Optional<Role> lock(String code) {
    List<UUID> ids =
        jdbc.queryForList(
            "SELECT id FROM roles WHERE code = :code FOR UPDATE", Map.of("code", code), UUID.class);
    return ids.isEmpty() ? Optional.empty() : find(code);
  }

  /** The codes among {@code codes} that are not in the catalogue. */
  public Set<String> unknownPermissions(Collection<String> codes) {
    if (codes.isEmpty()) {
      return Set.of();
    }
    Set<String> known =
        new LinkedHashSet<>(
            jdbc.queryForList(
                "SELECT code FROM permissions WHERE code IN (:codes)",
                Map.of("codes", codes),
                String.class));
    return codes.stream()
        .filter(code -> !known.contains(code))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Replaces the role's permissions; the caller holds the role lock. */
  public void replacePermissions(
      UUID roleId, Collection<String> codes, UUID changedBy, Instant at) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("roleId", roleId)
            .addValue("codes", codes)
            .addValue("changedBy", changedBy, Types.OTHER)
            .addValue("at", at.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    jdbc.update("DELETE FROM role_permissions WHERE role_id = :roleId", params);
    if (!codes.isEmpty()) {
      jdbc.update(
          """
          INSERT INTO role_permissions (role_id, permission_id)
          SELECT :roleId, p.id FROM permissions p WHERE p.code IN (:codes)
          """,
          params);
    }
    jdbc.update(
        """
        UPDATE roles SET version = version + 1, updated_at = :at, updated_by = :changedBy
        WHERE id = :roleId
        """,
        params);
  }

  private Map<UUID, List<String>> permissionsByRole(UUID roleId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    String filter = "";
    if (roleId != null) {
      filter = "WHERE rp.role_id = :roleId";
      params.addValue("roleId", roleId);
    }
    List<Map.Entry<UUID, String>> rows =
        jdbc.query(
            "SELECT rp.role_id, p.code FROM role_permissions rp JOIN permissions p"
                + " ON p.id = rp.permission_id "
                + filter
                + " ORDER BY p.code",
            params,
            (rs, row) -> Map.entry(rs.getObject("role_id", UUID.class), rs.getString("code")));
    return rows.stream()
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(ArrayList::new))));
  }

  private static Role role(ResultSet rs, Map<UUID, List<String>> permissions) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    return new Role(
        id,
        rs.getString("code"),
        rs.getString("description"),
        new LinkedHashSet<>(Arrays.asList(rs.getString("scope_types").split(","))),
        List.copyOf(permissions.getOrDefault(id, List.of())));
  }
}
