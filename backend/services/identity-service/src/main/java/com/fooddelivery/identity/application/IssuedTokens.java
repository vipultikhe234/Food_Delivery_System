package com.fooddelivery.identity.application;

import java.time.Instant;
import java.util.UUID;

/** The token pair handed to a client after register, login or refresh. */
public record IssuedTokens(
    UUID userId,
    UUID sessionId,
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt) {}
