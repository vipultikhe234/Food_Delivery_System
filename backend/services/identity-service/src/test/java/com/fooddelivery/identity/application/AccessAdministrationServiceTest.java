package com.fooddelivery.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fooddelivery.identity.application.AccessAdministrationService.Caller;
import com.fooddelivery.identity.application.AccessAdministrationService.RoleGrant;
import com.fooddelivery.identity.domain.Identifier;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore.Role;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Assignment;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Granted;
import com.fooddelivery.platform.events.EventEnvelope.Actor;
import com.fooddelivery.platform.events.audit.AuditRecorded;
import com.fooddelivery.platform.events.outbox.NewEvent;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import com.fooddelivery.platform.security.AuthenticatedUser;
import com.fooddelivery.platform.web.error.ApiException;
import com.fooddelivery.platform.web.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** REQ-AUTH-003 AC2 and AC5; audit records per REQ-AUDIT-001 AC1/AC2 (producer side). */
class AccessAdministrationServiceTest {

  static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");

  private final RoleCatalogStore catalog = mock(RoleCatalogStore.class);
  private final UserRoleStore userRoles = mock(UserRoleStore.class);
  private final UserRepository users = mock(UserRepository.class);
  private final OutboxPublisher outbox = mock(OutboxPublisher.class);
  private final AccessAdministrationService service =
      new AccessAdministrationService(
          catalog,
          userRoles,
          users,
          outbox,
          Transactions.direct(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private final UUID target = UUID.randomUUID();
  private final UUID branch = UUID.randomUUID();
  private final Caller superAdmin = caller("SUPER_ADMIN");
  private final Caller admin = caller("ADMIN");

  private final Role adminRole = role("ADMIN", "GLOBAL");
  private final Role superAdminRole = role("SUPER_ADMIN", "GLOBAL");
  private final Role cashier = role("CASHIER", "BRANCH");

  @BeforeEach
  void setUp() {
    when(users.findById(target))
        .thenReturn(
            Optional.of(User.register(Identifier.email("a@example.com").orElseThrow(), null, "h")));
    when(catalog.find("ADMIN")).thenReturn(Optional.of(adminRole));
    when(catalog.find("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
    when(catalog.find("CASHIER")).thenReturn(Optional.of(cashier));
    when(catalog.lock("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
  }

  @Test
  void onlyASuperAdminGrantsOrRevokesAdminRoles() {
    for (String role : List.of("ADMIN", "SUPER_ADMIN")) {
      assertThatThrownBy(
              () -> service.grant(admin, target, new RoleGrant(role, null, null, "promotion")))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }
    Assignment adminAssignment = assignment("ADMIN", "GLOBAL", null);
    when(userRoles.lock(target, adminAssignment.id())).thenReturn(Optional.of(adminAssignment));
    assertThatThrownBy(() -> service.revoke(admin, target, adminAssignment.id(), "left"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("FORBIDDEN");

    verify(userRoles, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    verify(userRoles, never()).delete(any());
    verify(outbox, never()).publish(any());
  }

  @Test
  void aGrantIsStoredAndAuditedWithActorRolesBeforeAfterReasonAndIp() {
    Assignment created = assignment("ADMIN", "GLOBAL", null);
    when(userRoles.insertIfAbsent(
            target, adminRole.id(), "GLOBAL", null, superAdmin.user().userId(), NOW))
        .thenReturn(new Granted(created, true));

    Granted granted =
        service.grant(superAdmin, target, new RoleGrant("ADMIN", null, null, "on-call cover"));

    assertThat(granted.created()).isTrue();
    NewEvent event = publishedEvent();
    assertThat(event.topic()).isEqualTo(AuditRecorded.TOPIC);
    assertThat(event.actor()).isEqualTo(Actor.user(superAdmin.user().userId()));
    AuditRecorded record = (AuditRecorded) event.payload();
    assertThat(record.action()).isEqualTo("ROLE_GRANTED");
    assertThat(record.entityType()).isEqualTo("User");
    assertThat(record.entityId()).isEqualTo(target);
    assertThat(record.actorRoles()).containsExactly("SUPER_ADMIN");
    assertThat(record.oldValue()).isNull();
    assertThat(record.newValue()).isEqualTo(AccessAdministrationService.describe(created));
    assertThat(record.reason()).isEqualTo("on-call cover");
    assertThat(record.ip()).isEqualTo("10.0.0.9");
  }

  @Test
  void grantingARoleTheUserAlreadyHoldsChangesNothing() {
    Assignment existing = assignment("CASHIER", "BRANCH", branch);
    when(userRoles.insertIfAbsent(any(), any(), any(), any(), any(), any()))
        .thenReturn(new Granted(existing, false));

    Granted granted =
        service.grant(admin, target, new RoleGrant("CASHIER", "BRANCH", branch, "rota"));

    assertThat(granted.created()).isFalse();
    verify(outbox, never()).publish(any());
  }

  @Test
  void theScopeMustFitTheRole() {
    assertThatThrownBy(
            () -> service.grant(admin, target, new RoleGrant("CASHIER", null, null, "r")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(field(e)).isEqualTo("scopeType"));
    assertThatThrownBy(
            () -> service.grant(admin, target, new RoleGrant("CASHIER", "BRANCH", null, "r")))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(field(e)).isEqualTo("scopeId"));
    assertThatThrownBy(
            () ->
                service.grant(
                    superAdmin, target, new RoleGrant("ADMIN", "GLOBAL", UUID.randomUUID(), "r")))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(field(e)).isEqualTo("scopeId"));
    assertThatThrownBy(() -> service.grant(admin, target, new RoleGrant("PILOT", null, null, "r")))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(field(e)).isEqualTo("role"));
  }

  @Test
  void unknownOrDeletedUsersAreNotFound() {
    UUID nobody = UUID.randomUUID();
    when(users.findById(nobody)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assignments(nobody))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    assertThatThrownBy(
            () -> service.grant(admin, nobody, new RoleGrant("CASHIER", "BRANCH", branch, "r")))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
  }

  @Test
  void theLastSuperAdminCannotBeRemoved() {
    Assignment last = assignment("SUPER_ADMIN", "GLOBAL", null);
    when(userRoles.lock(target, last.id())).thenReturn(Optional.of(last));
    when(userRoles.holders(superAdminRole.id())).thenReturn(1);

    assertThatThrownBy(() -> service.revoke(superAdmin, target, last.id(), "cleanup"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("last SUPER_ADMIN");
    verify(userRoles, never()).delete(any());

    when(userRoles.holders(superAdminRole.id())).thenReturn(2);
    service.revoke(superAdmin, target, last.id(), "cleanup");
    verify(userRoles).delete(last.id());
  }

  @Test
  void aRevocationIsAuditedWithTheRemovedAssignment() {
    Assignment assignment = assignment("CASHIER", "BRANCH", branch);
    when(userRoles.lock(target, assignment.id())).thenReturn(Optional.of(assignment));

    service.revoke(admin, target, assignment.id(), "left the outlet");

    verify(userRoles).delete(assignment.id());
    AuditRecorded record = (AuditRecorded) publishedEvent().payload();
    assertThat(record.action()).isEqualTo("ROLE_REVOKED");
    assertThat(record.oldValue()).isEqualTo(AccessAdministrationService.describe(assignment));
    assertThat(record.newValue()).isNull();
    assertThat(record.reason()).isEqualTo("left the outlet");
  }

  @Test
  void rolePermissionChangesAreValidatedAndAudited() {
    Role before = new Role(cashier.id(), "CASHIER", "d", Set.of("BRANCH"), List.of("POS_ORDER"));
    Role after =
        new Role(cashier.id(), "CASHIER", "d", Set.of("BRANCH"), List.of("BILL_VIEW", "POS_ORDER"));
    when(catalog.unknownPermissions(anyCollection())).thenReturn(Set.of());
    when(catalog.lock("CASHIER")).thenReturn(Optional.of(before));
    when(catalog.find("CASHIER")).thenReturn(Optional.of(after));

    Role result =
        service.changeRolePermissions(
            superAdmin, "CASHIER", List.of("POS_ORDER", "BILL_VIEW"), "cashiers reprint bills");

    assertThat(result).isEqualTo(after);
    verify(catalog)
        .replacePermissions(
            eq(cashier.id()),
            eq(Set.of("BILL_VIEW", "POS_ORDER")),
            eq(superAdmin.user().userId()),
            eq(NOW));
    AuditRecorded record = (AuditRecorded) publishedEvent().payload();
    assertThat(record.action()).isEqualTo("ROLE_PERMISSIONS_CHANGED");
    assertThat(record.entityType()).isEqualTo("Role");
    assertThat(record.oldValue())
        .isEqualTo(Map.of("role", "CASHIER", "permissions", List.of("POS_ORDER")));
    assertThat(record.newValue())
        .isEqualTo(Map.of("role", "CASHIER", "permissions", List.of("BILL_VIEW", "POS_ORDER")));
  }

  @Test
  void unknownPermissionsTheSuperAdminRoleAndNoOpChangesAreHandled() {
    when(catalog.unknownPermissions(anyCollection())).thenReturn(Set.of("FLY"));
    assertThatThrownBy(
            () -> service.changeRolePermissions(superAdmin, "CASHIER", List.of("FLY"), "r"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(field(e)).isEqualTo("permissions"));

    assertThatThrownBy(
            () -> service.changeRolePermissions(superAdmin, "SUPER_ADMIN", List.of(), "r"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

    Role unchanged = new Role(cashier.id(), "CASHIER", "d", Set.of("BRANCH"), List.of("POS_ORDER"));
    when(catalog.unknownPermissions(anyCollection())).thenReturn(Set.of());
    when(catalog.lock("CASHIER")).thenReturn(Optional.of(unchanged));
    assertThat(service.changeRolePermissions(superAdmin, "CASHIER", List.of("POS_ORDER"), "r"))
        .isEqualTo(unchanged);

    verify(catalog, never()).replacePermissions(any(), anyCollection(), any(), any());
    verify(outbox, never()).publish(any());
  }

  private NewEvent publishedEvent() {
    ArgumentCaptor<NewEvent> captor = ArgumentCaptor.forClass(NewEvent.class);
    verify(outbox).publish(captor.capture());
    return captor.getValue();
  }

  private static String field(ApiException e) {
    assertThat(e.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
    return e.errors().getFirst().field();
  }

  private static Caller caller(String role) {
    return new Caller(
        new AuthenticatedUser(
            UUID.randomUUID(), UUID.randomUUID(), Set.of(role), Set.of(), List.of()),
        "10.0.0.9");
  }

  private static Role role(String code, String scopeType) {
    return new Role(UUID.randomUUID(), code, code, Set.of(scopeType), List.of());
  }

  private Assignment assignment(String role, String scopeType, UUID scopeId) {
    return new Assignment(UUID.randomUUID(), target, role, scopeType, scopeId, null, NOW);
  }
}
