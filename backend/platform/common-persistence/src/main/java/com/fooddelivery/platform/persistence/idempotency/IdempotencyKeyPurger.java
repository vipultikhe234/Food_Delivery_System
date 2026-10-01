package com.fooddelivery.platform.persistence.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Deletes expired idempotency records (REQ-PLAT-006 AC5). Expired records are already ignored when
 * a key is reused, so this only keeps the table small.
 */
public class IdempotencyKeyPurger {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurger.class);

  private final JdbcTemplate jdbc;

  public IdempotencyKeyPurger(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(
      fixedDelayString = "${fdp.persistence.idempotency.purge-interval:1h}",
      initialDelayString = "${fdp.persistence.idempotency.purge-interval:1h}")
  public int purge() {
    int deleted = jdbc.update("DELETE FROM idempotency_keys WHERE expires_at <= now()");
    if (deleted > 0) {
      log.info("Purged {} expired idempotency keys", deleted);
    }
    return deleted;
  }
}
