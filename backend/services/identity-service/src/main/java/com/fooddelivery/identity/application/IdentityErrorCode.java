package com.fooddelivery.identity.application;

import com.fooddelivery.platform.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Auth error codes (docs/07-api-design.md §3.2, "Auth"; REQ-AUTH-001 AC1 for the duplicate code).
 */
public enum IdentityErrorCode implements ErrorCode {
  USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "User already exists"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
  ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account locked"),
  ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN, "Account blocked"),
  REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token invalid"),
  REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token reused"),
  PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "Password does not meet the policy");

  private final HttpStatus status;
  private final String title;

  IdentityErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  @Override
  public String code() {
    return name();
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String title() {
    return title;
  }
}
