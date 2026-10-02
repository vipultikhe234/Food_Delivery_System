package com.fooddelivery.identity.api;

import com.fooddelivery.identity.application.IssuedTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Token pair returned by register, login and refresh. Lifetimes are in seconds, counted from the
 * response.
 */
record TokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    long refreshExpiresIn,
    UUID userId,
    UUID sessionId) {

  static TokenResponse of(IssuedTokens tokens, Instant now) {
    return new TokenResponse(
        tokens.accessToken(),
        "Bearer",
        seconds(now, tokens.accessTokenExpiresAt()),
        tokens.refreshToken(),
        seconds(now, tokens.refreshTokenExpiresAt()),
        tokens.userId(),
        tokens.sessionId());
  }

  private static long seconds(Instant now, Instant expiresAt) {
    return Math.max(0, Duration.between(now, expiresAt).toSeconds());
  }
}
