package com.fooddelivery.platform.persistence.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code fdp.persistence.idempotency.*}. The TTL default of 24 h is the PROPOSED value of
 * REQ-PLAT-006 AC5.
 *
 * @param inProgressTimeout after this long an unfinished record (crashed instance) may be taken
 *     over by a retry with the same payload
 */
@ConfigurationProperties("fdp.persistence.idempotency")
public record IdempotencyProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("24h") Duration ttl,
    @DefaultValue("60s") Duration inProgressTimeout,
    @DefaultValue("1h") Duration purgeInterval) {}
