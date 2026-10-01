package com.fooddelivery.platform.persistence.migration;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

/**
 * Runs the service's own migrations, then each enabled platform feature with the same connection
 * settings (the {@code <service>_owner} role, docs/06 §1.1).
 *
 * <p>A feature enabled later finds a non-empty schema without its history table; {@code
 * baselineOnMigrate} with baseline version 0 lets its V1 still run.
 */
public class PlatformMigrationStrategy implements FlywayMigrationStrategy {

  private static final Logger log = LoggerFactory.getLogger(PlatformMigrationStrategy.class);

  private final List<PlatformMigration> migrations;

  public PlatformMigrationStrategy(List<PlatformMigration> migrations) {
    this.migrations = List.copyOf(migrations);
  }

  @Override
  public void migrate(Flyway flyway) {
    flyway.migrate();
    for (PlatformMigration migration : migrations) {
      log.info("Applying platform migrations for {}", migration.name());
      Flyway.configure(flyway.getConfiguration().getClassLoader())
          .configuration(flyway.getConfiguration())
          .locations(migration.location())
          .table(migration.historyTable())
          .baselineOnMigrate(true)
          .baselineVersion("0")
          .load()
          .migrate();
    }
  }
}
