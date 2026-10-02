package com.fooddelivery.identity.application;

import com.fooddelivery.identity.config.IdentityProperties;
import com.fooddelivery.identity.domain.Identifier;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.domain.UserStatus;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore.Role;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Granted;
import com.fooddelivery.platform.events.EventEnvelope.Actor;
import com.fooddelivery.platform.events.audit.AuditRecorded;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Makes the configured, already registered account the first SUPER_ADMIN (owner decision
 * 2026-10-02). It does nothing once any SUPER_ADMIN exists, so the setting cannot be used to add
 * more of them later. No password is ever configured; the identifier is not logged.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

  static final String REASON =
      "Bootstrap of the first SUPER_ADMIN (IDENTITY_BOOTSTRAP_SUPER_ADMIN)";

  public enum Outcome {
    NOT_CONFIGURED,
    ALREADY_PRESENT,
    ACCOUNT_NOT_FOUND,
    GRANTED
  }

  private final IdentityProperties properties;
  private final RoleCatalogStore catalog;
  private final UserRoleStore userRoles;
  private final UserRepository users;
  private final OutboxPublisher outbox;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public SuperAdminBootstrap(
      IdentityProperties properties,
      RoleCatalogStore catalog,
      UserRoleStore userRoles,
      UserRepository users,
      OutboxPublisher outbox,
      TransactionTemplate transactions,
      Clock clock) {
    this.properties = properties;
    this.catalog = catalog;
    this.userRoles = userRoles;
    this.users = users;
    this.outbox = outbox;
    this.transactions = transactions;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    Outcome outcome = bootstrap();
    switch (outcome) {
      case GRANTED -> log.warn("The configured account was made the first SUPER_ADMIN.");
      case ACCOUNT_NOT_FOUND ->
          log.warn(
              "IDENTITY_BOOTSTRAP_SUPER_ADMIN names no active account; register it and restart.");
      case ALREADY_PRESENT, NOT_CONFIGURED -> log.debug("SUPER_ADMIN bootstrap: {}", outcome);
    }
  }

  public Outcome bootstrap() {
    String configured = properties.bootstrap().superAdmin();
    if (configured == null || configured.isBlank()) {
      return Outcome.NOT_CONFIGURED;
    }
    return transactions.execute(
        status -> {
          // The role lock serialises replicas starting at the same time.
          Role superAdmin = catalog.lock(AccessAdministrationService.SUPER_ADMIN).orElseThrow();
          if (userRoles.holders(superAdmin.id()) > 0) {
            return Outcome.ALREADY_PRESENT;
          }
          Optional<User> account =
              Identifier.parse(configured)
                  .flatMap(
                      identifier ->
                          identifier.type() == Identifier.Type.EMAIL
                              ? users.findByEmail(identifier.value())
                              : users.findByPhone(identifier.value()))
                  .filter(user -> user.getStatus() == UserStatus.ACTIVE);
          if (account.isEmpty()) {
            return Outcome.ACCOUNT_NOT_FOUND;
          }
          User user = account.get();
          Granted granted =
              userRoles.insertIfAbsent(
                  user.getId(), superAdmin.id(), UserRoleStore.GLOBAL, null, null, clock.instant());
          outbox.publish(
              AuditRecorded.of(
                      List.of(),
                      AccessAdministrationService.ROLE_GRANTED,
                      "User",
                      user.getId(),
                      null,
                      AccessAdministrationService.describe(granted.assignment()),
                      REASON,
                      null)
                  .toEvent(Actor.SYSTEM));
          return Outcome.GRANTED;
        });
  }
}
