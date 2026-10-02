package com.fooddelivery.identity.domain;

import com.fooddelivery.platform.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Credentials of one account (docs/06 §2.1). Profile data such as the name belongs to user-service,
 * which receives it in {@code UserRegistered}.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Column(name = "phone", length = 16)
  private String phone;

  /** Stored lower-case; the column is citext so the unique index ignores case as well. */
  @Column(name = "email", columnDefinition = "citext")
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private UserStatus status;

  @Column(name = "phone_verified_at")
  private Instant phoneVerifiedAt;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  protected User() {}

  /**
   * A self-registered account. It is ACTIVE at once; phone and e-mail stay unverified until OTP
   * verification (REQ-AUTH-002), and features that need a verified contact check the timestamps.
   */
  public static User register(Identifier email, Identifier phone, String passwordHash) {
    if (email == null && phone == null) {
      throw new IllegalArgumentException("an e-mail address or a phone number is required");
    }
    User user = new User();
    user.email = email == null ? null : email.value();
    user.phone = phone == null ? null : phone.value();
    user.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    user.status = UserStatus.ACTIVE;
    return user;
  }

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getPhoneVerifiedAt() {
    return phoneVerifiedAt;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }
}
