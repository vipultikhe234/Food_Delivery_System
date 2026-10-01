package com.fooddelivery.platform.persistence.migration;

import java.util.regex.Pattern;

/**
 * Technical tables owned by a platform library (outbox, processed events, idempotency keys; docs/06
 * §1.3). Each feature keeps its own Flyway history table, so its versions never collide with the
 * service's migrations and a service can enable the feature at any time.
 */
public record PlatformMigration(String name, String location) {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,30}");

  public PlatformMigration {
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("invalid platform migration name: " + name);
    }
  }

  public String historyTable() {
    return "fdp_" + name + "_schema_history";
  }
}
