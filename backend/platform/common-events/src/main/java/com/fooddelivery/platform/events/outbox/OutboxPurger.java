package com.fooddelivery.platform.events.outbox;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/** Nightly purge of published outbox rows (docs/06 §6: published rows are kept 7 days). */
public class OutboxPurger {

  private static final Logger log = LoggerFactory.getLogger(OutboxPurger.class);

  private final JdbcTemplate jdbc;
  private final Duration retention;

  public OutboxPurger(JdbcTemplate jdbc, Duration retention) {
    this.jdbc = jdbc;
    this.retention = retention;
  }

  @Scheduled(cron = "${fdp.events.purge-cron:0 15 3 * * *}", zone = "UTC")
  public int purge() {
    int deleted =
        jdbc.update(
            "DELETE FROM outbox_events WHERE published_at < now() - make_interval(secs => ?)",
            retention.toSeconds());
    log.info("Purged {} published outbox rows", deleted);
    return deleted;
  }
}
