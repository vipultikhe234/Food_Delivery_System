package com.fooddelivery.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fooddelivery.identity.application.SuperAdminBootstrap.Outcome;
import com.fooddelivery.identity.config.IdentityProperties;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** First SUPER_ADMIN from IDENTITY_BOOTSTRAP_SUPER_ADMIN (owner decision 2026-10-02). */
class SuperAdminBootstrapTest {

  static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");

  private final RoleCatalogStore catalog = mock(RoleCatalogStore.class);
  private final UserRoleStore userRoles = mock(UserRoleStore.class);
  private final UserRepository users = mock(UserRepository.class);
  private final OutboxPublisher outbox = mock(OutboxPublisher.class);
  private final Role superAdmin =
      new Role(UUID.randomUUID(), "SUPER_ADMIN", "d", Set.of("GLOBAL"), List.of());
  private final User account =
      User.register(Identifier.email("owner@example.com").orElseThrow(), null, "hash");

  @BeforeEach
  void setUp() {
    when(catalog.lock("SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
  }

  @Test
  void doesNothingWhenNotConfigured() {
    assertThat(bootstrap(null).bootstrap()).isEqualTo(Outcome.NOT_CONFIGURED);
    assertThat(bootstrap(" ").bootstrap()).isEqualTo(Outcome.NOT_CONFIGURED);
    verifyNoInteractions(catalog, userRoles, users, outbox);
  }

  @Test
  void neverAddsASecondSuperAdmin() {
    when(userRoles.holders(superAdmin.id())).thenReturn(1);

    assertThat(bootstrap("owner@example.com").bootstrap()).isEqualTo(Outcome.ALREADY_PRESENT);

    verifyNoInteractions(users, outbox);
    verify(userRoles, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void anUnregisteredAccountIsReportedNotCreated() {
    when(users.findByPhone("+919876543210")).thenReturn(Optional.empty());

    assertThat(bootstrap("+919876543210").bootstrap()).isEqualTo(Outcome.ACCOUNT_NOT_FOUND);
    assertThat(bootstrap("not an identifier").bootstrap()).isEqualTo(Outcome.ACCOUNT_NOT_FOUND);

    verify(userRoles, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    verifyNoInteractions(outbox);
  }

  @Test
  void grantsTheRoleAndAuditsItAsTheSystem() {
    when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(account));
    Assignment assignment =
        new Assignment(
            UUID.randomUUID(), account.getId(), "SUPER_ADMIN", "GLOBAL", null, null, NOW);
    when(userRoles.insertIfAbsent(account.getId(), superAdmin.id(), "GLOBAL", null, null, NOW))
        .thenReturn(new Granted(assignment, true));

    assertThat(bootstrap("Owner@Example.com").bootstrap()).isEqualTo(Outcome.GRANTED);

    ArgumentCaptor<NewEvent> event = ArgumentCaptor.forClass(NewEvent.class);
    verify(outbox).publish(event.capture());
    assertThat(event.getValue().actor()).isEqualTo(Actor.SYSTEM);
    AuditRecorded record = (AuditRecorded) event.getValue().payload();
    assertThat(record.action()).isEqualTo("ROLE_GRANTED");
    assertThat(record.entityId()).isEqualTo(account.getId());
    assertThat(record.actorRoles()).isEmpty();
    assertThat(record.newValue()).isEqualTo(AccessAdministrationService.describe(assignment));
    assertThat(record.reason()).isEqualTo(SuperAdminBootstrap.REASON);
  }

  private SuperAdminBootstrap bootstrap(String configured) {
    IdentityProperties properties =
        new IdentityProperties(
            new IdentityProperties.Jwt(null, true),
            new IdentityProperties.Tokens(Duration.ofMinutes(15), Duration.ofDays(30)),
            new IdentityProperties.Events(1, (short) 1, 1),
            new IdentityProperties.Bootstrap(configured));
    return new SuperAdminBootstrap(
        properties,
        catalog,
        userRoles,
        users,
        outbox,
        Transactions.direct(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
