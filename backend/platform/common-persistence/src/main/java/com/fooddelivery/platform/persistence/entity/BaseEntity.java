package com.fooddelivery.platform.persistence.entity;

import com.fooddelivery.platform.persistence.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base columns of every core entity (docs/06 §1.2, REQ-PLAT-008 AC3): a UUIDv7 id assigned at
 * construction, audit timestamps and actors filled by JPA auditing, and an optimistic-lock {@code
 * version}. A stale version on update raises an optimistic locking failure, which the web layer
 * returns as 409 {@code CONCURRENT_MODIFICATION} (AC4).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Persistable<UUID> {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id = UuidV7.generate();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @LastModifiedBy
  @Column(name = "updated_by")
  private UUID updatedBy;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Transient private boolean persisted;

  @Override
  public UUID getId() {
    return id;
  }

  /** The id is known before saving, so new rows are detected by this flag, not by a null id. */
  @Override
  public boolean isNew() {
    return !persisted;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    persisted = true;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Long getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof BaseEntity entity
        && getClass().equals(entity.getClass())
        && id.equals(entity.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
