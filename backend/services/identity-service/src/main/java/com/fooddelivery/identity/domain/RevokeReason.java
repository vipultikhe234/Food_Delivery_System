package com.fooddelivery.identity.domain;

/** Why a refresh token stopped working ({@code ck_refresh_tokens_revoke_reason}). */
public enum RevokeReason {
  LOGOUT,
  LOGOUT_ALL,
  REUSE_DETECTED,
  PASSWORD_RESET,
  BLOCKED,
  ADMIN
}
