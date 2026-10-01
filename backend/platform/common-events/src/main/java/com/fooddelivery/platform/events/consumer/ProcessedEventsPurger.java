package com.fooddelivery.platform.events.consumer;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/** Nightly purge of processed event ids (docs/06 §6: kept 14 days). */
public class ProcessedEventsPurger {

  private static final Logger log = LoggerFactory.getLogger(ProcessedEventsPurger.class);

  private final JdbcTemplate jdbc;
  private final Duration retention;

  public ProcessedEventsPurger(JdbcTemplate jdbc, Duration retention) {
    this.jdbc = jdbc;
    this.retention = retention;
  }

  @Scheduled(cron = "${fdp.events.purge-cron:0 15 3 * * *}", zone = "UTC")
  public int purge() {
    int deleted =
        jdbc.update(
            "DELETE FROM processed_events WHERE processed_at < now() - make_interval(secs => ?)",
            retention.toSeconds());
    log.info("Purged {} processed event ids", deleted);
    return deleted;
  }
}
