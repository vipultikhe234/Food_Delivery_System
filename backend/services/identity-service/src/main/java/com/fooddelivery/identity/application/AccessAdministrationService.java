package com.fooddelivery.identity.application;

import com.fooddelivery.identity.domain.UserStatus;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore.Permission;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore.Role;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Assignment;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Granted;
import com.fooddelivery.platform.events.EventEnvelope.Actor;
import com.fooddelivery.platform.events.audit.AuditRecorded;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import com.fooddelivery.platform.security.AuthenticatedUser;
import com.fooddelivery.platform.web.error.ApiException;
import com.fooddelivery.platform.web.error.CommonErrorCode;
import com.fooddelivery.platform.web.error.FieldViolation;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Role and permission administration (REQ-AUTH-003 AC2, AC5). Every change is audited through the
 * outbox in its own transaction (REQ-AUDIT-001 AC4). Changed grants reach access tokens at the next
 * refresh, within the 15-minute token lifetime; immediate revocation needs the Redis deny-list
 * (KI-020).
 */
@Service
public class AccessAdministrationService {

  public static final String SUPER_ADMIN = "SUPER_ADMIN";

  /** Only a SUPER_ADMIN may grant or revoke these (REQ-AUTH-003 AC5, docs/09 §3.3 notes). */
  static final Set<String> PRIVILEGED_ROLES = Set.of("ADMIN", SUPER_ADMIN);

  static final String ROLE_GRANTED = "ROLE_GRANTED";
  static final String ROLE_REVOKED = "ROLE_REVOKED";
  static final String ROLE_PERMISSIONS_CHANGED = "ROLE_PERMISSIONS_CHANGED";

  /** The administrator acting, and the address the request came from. */
  public record Caller(AuthenticatedUser user, String ip) {}

  /**
   * @param scopeType GLOBAL when null
   */
  public record RoleGrant(String role, String scopeType, UUID scopeId, String reason) {}

  private final RoleCatalogStore catalog;
  private final UserRoleStore userRoles;
  private final UserRepository users;
  private final OutboxPublisher outbox;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public AccessAdministrationService(
      RoleCatalogStore catalog,
      UserRoleStore userRoles,
      UserRepository users,
      OutboxPublisher outbox,
      TransactionTemplate transactions,
      Clock clock) {
    this.catalog = catalog;
    this.userRoles = userRoles;
    this.users = users;
    this.outbox = outbox;
    this.transactions = transactions;
    this.clock = clock;
  }

  public List<Permission> permissions() {
    return catalog.permissions();
  }

  public List<Role> roles() {
    return catalog.roles();
  }

  /** Replaces the permissions of a role. SUPER_ADMIN itself is protected against lock-out. */
  public Role changeRolePermissions(
      Caller caller, String roleCode, Collection<String> permissions, String reason) {
    if (SUPER_ADMIN.equals(roleCode)) {
      throw forbidden("The SUPER_ADMIN role cannot be changed through the API.");
    }
    Set<String> requested = new TreeSet<>(permissions);
    Set<String> unknown = catalog.unknownPermissions(requested);
    if (!unknown.isEmpty()) {
      throw invalid(
          new FieldViolation(
              "permissions", "UNKNOWN", "unknown permissions: " + String.join(", ", unknown)));
    }
    return transactions.execute(
        status -> {
          Role role = catalog.lock(roleCode).orElseThrow(() -> notFound("The role was not found."));
          Set<String> before = new TreeSet<>(role.permissions());
          if (before.equals(requested)) {
            return role;
          }
          catalog.replacePermissions(role.id(), requested, caller.user().userId(), clock.instant());
          audit(
              caller,
              ROLE_PERMISSIONS_CHANGED,
              "Role",
              role.id(),
              Map.of("role", role.code(), "permissions", List.copyOf(before)),
              Map.of("role", role.code(), "permissions", List.copyOf(requested)),
              reason);
          return catalog.find(roleCode).orElseThrow();
        });
  }

  public List<Assignment> assignments(UUID userId) {
    requireUser(userId);
    return userRoles.forUser(userId);
  }

  /** Grants a role; granting one the user already holds returns the existing assignment. */
  public Granted grant(Caller caller, UUID userId, RoleGrant grant) {
    if (PRIVILEGED_ROLES.contains(grant.role()) && !caller.user().hasRole(SUPER_ADMIN)) {
      throw forbidden("Only a SUPER_ADMIN can grant this role.");
    }
    String scopeType = grant.scopeType() == null ? UserRoleStore.GLOBAL : grant.scopeType();
    Role role =
        catalog
            .find(grant.role())
            .orElseThrow(() -> invalid(new FieldViolation("role", "UNKNOWN", "unknown role")));
    if (!role.scopeTypes().contains(scopeType)) {
      throw invalid(
          new FieldViolation(
              "scopeType",
              "NOT_ALLOWED",
              "role "
                  + role.code()
                  + " is assigned with scope "
                  + String.join(" or ", role.scopeTypes())));
    }
    if (UserRoleStore.GLOBAL.equals(scopeType) != (grant.scopeId() == null)) {
      throw invalid(
          new FieldViolation(
              "scopeId",
              "INVALID",
              "scopeId is required for a scoped role and not allowed otherwise"));
    }
    return transactions.execute(
        status -> {
          requireUser(userId);
          Granted granted =
              userRoles.insertIfAbsent(
                  userId,
                  role.id(),
                  scopeType,
                  grant.scopeId(),
                  caller.user().userId(),
                  clock.instant());
          if (granted.created()) {
            audit(
                caller,
                ROLE_GRANTED,
                "User",
                userId,
                null,
                describe(granted.assignment()),
                grant.reason());
          }
          return granted;
        });
  }

  public void revoke(Caller caller, UUID userId, UUID assignmentId, String reason) {
    transactions.executeWithoutResult(
        status -> {
          Assignment assignment =
              userRoles
                  .lock(userId, assignmentId)
                  .orElseThrow(() -> notFound("The role assignment was not found."));
          if (PRIVILEGED_ROLES.contains(assignment.role()) && !caller.user().hasRole(SUPER_ADMIN)) {
            throw forbidden("Only a SUPER_ADMIN can revoke this role.");
          }
          if (SUPER_ADMIN.equals(assignment.role())) {
            Role superAdmin = catalog.lock(SUPER_ADMIN).orElseThrow();
            if (userRoles.holders(superAdmin.id()) <= 1) {
              throw forbidden("The last SUPER_ADMIN cannot be removed.");
            }
          }
          userRoles.delete(assignment.id());
          audit(caller, ROLE_REVOKED, "User", userId, describe(assignment), null, reason);
        });
  }

  private void requireUser(UUID userId) {
    users
        .findById(userId)
        .filter(user -> user.getStatus() != UserStatus.DELETED)
        .orElseThrow(() -> notFound("The user was not found."));
  }

  private void audit(
      Caller caller,
      String action,
      String entityType,
      UUID entityId,
      Object oldValue,
      Object newValue,
      String reason) {
    AuthenticatedUser user = caller.user();
    outbox.publish(
        AuditRecorded.of(
                List.copyOf(new TreeSet<>(user.roles())),
                action,
                entityType,
                entityId,
                oldValue,
                newValue,
                reason,
                caller.ip())
            .toEvent(Actor.user(user.userId())));
  }

  static Map<String, Object> describe(Assignment assignment) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("assignmentId", assignment.id());
    value.put("role", assignment.role());
    value.put("scopeType", assignment.scopeType());
    value.put("scopeId", assignment.scopeId());
    return value;
  }

  private static ApiException forbidden(String detail) {
    return new ApiException(CommonErrorCode.FORBIDDEN, detail);
  }

  private static ApiException notFound(String detail) {
    return new ApiException(CommonErrorCode.NOT_FOUND, detail);
  }

  private static ApiException invalid(FieldViolation violation) {
    return new ApiException(
        CommonErrorCode.VALIDATION_FAILED,
        "The request is invalid.",
        List.of(violation),
        Map.of(),
        null);
  }
}
