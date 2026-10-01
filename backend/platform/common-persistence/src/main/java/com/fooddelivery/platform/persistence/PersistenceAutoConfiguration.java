package com.fooddelivery.platform.persistence;

import com.fooddelivery.platform.persistence.entity.SecurityContextAuditor;
import com.fooddelivery.platform.persistence.idempotency.IdempotencyKeyPurger;
import com.fooddelivery.platform.persistence.idempotency.IdempotencyProperties;
import com.fooddelivery.platform.persistence.idempotency.IdempotencyService;
import com.fooddelivery.platform.persistence.migration.PlatformMigration;
import com.fooddelivery.platform.persistence.migration.PlatformMigrationStrategy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persistence conventions for every service with a database (REQ-PLAT-008): JPA auditing of the
 * base columns, platform migrations, and optional idempotency keys (REQ-PLAT-006).
 *
 * <p>Services must not add their own {@code @EnableJpaAuditing}; it is enabled here.
 */
@AutoConfiguration(
    after = DataSourceAutoConfiguration.class,
    before = {FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@EnableConfigurationProperties(IdempotencyProperties.class)
public class PersistenceAutoConfiguration {

  static final String IDEMPOTENCY_MIGRATIONS = "classpath:db/fdp/idempotency";

  @Bean
  @ConditionalOnMissingBean(FlywayMigrationStrategy.class)
  PlatformMigrationStrategy platformMigrationStrategy(ObjectProvider<PlatformMigration> features) {
    return new PlatformMigrationStrategy(features.orderedStream().toList());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableJpaAuditing(
      auditorAwareRef = "fdpAuditorAware",
      dateTimeProviderRef = "fdpAuditingDateTimeProvider")
  static class AuditingConfiguration {

    @Bean
    DateTimeProvider fdpAuditingDateTimeProvider() {
      return () -> Optional.of(Instant.now());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
  static class SecurityAuditorConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "fdpAuditorAware")
    AuditorAware<UUID> fdpAuditorAware() {
      return new SecurityContextAuditor();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingClass("org.springframework.security.core.context.SecurityContextHolder")
  static class NoAuditorConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "fdpAuditorAware")
    AuditorAware<UUID> fdpAuditorAware() {
      return Optional::empty;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "fdp.persistence.idempotency.enabled", havingValue = "true")
  @EnableScheduling
  static class IdempotencyConfiguration {

    @Bean
    PlatformMigration idempotencyMigration() {
      return new PlatformMigration("idempotency", IDEMPOTENCY_MIGRATIONS);
    }

    @Bean
    IdempotencyService idempotencyService(
        NamedParameterJdbcTemplate jdbc,
        PlatformTransactionManager transactions,
        JsonMapper json,
        IdempotencyProperties properties) {
      return new IdempotencyService(jdbc, transactions, json, properties);
    }

    @Bean
    IdempotencyKeyPurger idempotencyKeyPurger(JdbcTemplate jdbc) {
      return new IdempotencyKeyPurger(jdbc);
    }
  }
}
