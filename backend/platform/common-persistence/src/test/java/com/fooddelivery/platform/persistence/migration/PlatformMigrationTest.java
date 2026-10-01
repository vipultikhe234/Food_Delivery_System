package com.fooddelivery.platform.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PlatformMigrationTest {

  @Test
  void eachFeatureHasItsOwnHistoryTable() {
    assertThat(new PlatformMigration("outbox", "classpath:db/fdp/outbox").historyTable())
        .isEqualTo("fdp_outbox_schema_history");
  }

  @Test
  void namesMustBeSafeTableNameParts() {
    for (String name : new String[] {"", "Outbox", "1outbox", "out-box", "x; drop table y"}) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new PlatformMigration(name, "classpath:db/fdp/x"));
    }
  }
}
