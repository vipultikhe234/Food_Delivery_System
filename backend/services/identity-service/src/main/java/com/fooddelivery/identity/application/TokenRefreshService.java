package com.fooddelivery.identity.application;

import com.fooddelivery.identity.domain.RevokeReason;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.domain.UserStatus;
import com.fooddelivery.identity.infrastructure.persistence.RefreshTokenStore;
import com.fooddelivery.identity.infrastructure.persistence.RefreshTokenStore.StoredToken;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.identity.infrastructure.token.RefreshTokens;
import com.fooddelivery.platform.web.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refresh-token rotation with reuse detection (REQ-AUTH-001 AC4, docs/09-security.md §2.2). Every
 * refresh consumes the presented token. Presenting a consumed token again means it was copied, so
 * the whole family is revoked: neither the thief nor the owner can continue that session.
 */
@Service
public class TokenRefreshService {

  private static final Logger log = LoggerFactory.getLogger(TokenRefreshService.class);

  private enum Result {
    ROTATED,
    INVALID,
    REUSED,
    BLOCKED
  }

  private record Outcome(Result result, IssuedTokens tokens, UUID familyId) {
    static Outcome of(Result result) {
      return new Outcome(result, null, null);
    }
  }

  private final RefreshTokenStore store;
  private final UserRepository users;
  private final SessionTokenService sessions;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public TokenRefreshService(
      RefreshTokenStore store,
      UserRepository users,
      SessionTokenService sessions,
      TransactionTemplate transactions,
      Clock clock) {
    this.store = store;
    this.users = users;
    this.sessions = sessions;
    this.transactions = transactions;
    this.clock = clock;
  }

  public IssuedTokens refresh(String refreshToken, ClientContext client) {
    if (refreshToken == null
        || refreshToken.isBlank()
        || refreshToken.length() > RefreshTokens.MAX_LENGTH) {
      throw invalid();
    }
    String hash = RefreshTokens.hash(refreshToken);
    // The revocation for reuse or a blocked account must commit, so the outcome is decided inside
    // the transaction and the error is raised after it.
    Outcome outcome = transactions.execute(status -> rotate(hash, client));
    return switch (outcome.result()) {
      case ROTATED -> outcome.tokens();
      case INVALID -> throw invalid();
      case REUSED -> {
        log.warn(
            "Refresh token reuse detected; token family {} revoked (REFRESH_TOKEN_REUSED)",
            outcome.familyId());
        throw new ApiException(
            IdentityErrorCode.REFRESH_TOKEN_REUSED,
            "This refresh token was already used. Sign in again.");
      }
      case BLOCKED ->
          throw new ApiException(
              IdentityErrorCode.ACCOUNT_BLOCKED, "This account has been blocked. Contact support.");
    };
  }

  private Outcome rotate(String hash, ClientContext client) {
    Instant now = clock.instant();
    StoredToken stored = store.findForUpdate(hash).orElse(null);
    if (stored == null || stored.revokedAt() != null) {
      return Outcome.of(Result.INVALID);
    }
    if (stored.rotatedAt() != null) {
      store.revokeFamily(stored.familyId(), now, RevokeReason.REUSE_DETECTED);
      return new Outcome(Result.REUSED, null, stored.familyId());
    }
    if (!stored.expiresAt().isAfter(now)) {
      return Outcome.of(Result.INVALID);
    }
    UserStatus status =
        users.findById(stored.userId()).map(User::getStatus).orElse(UserStatus.DELETED);
    if (status == UserStatus.BLOCKED || status == UserStatus.DELETED) {
      store.revokeFamily(stored.familyId(), now, RevokeReason.BLOCKED);
      return Outcome.of(status == UserStatus.BLOCKED ? Result.BLOCKED : Result.INVALID);
    }
    store.markRotated(stored.id(), now);
    return new Outcome(Result.ROTATED, sessions.continueSession(stored, client), null);
  }

  private static ApiException invalid() {
    return new ApiException(
        IdentityErrorCode.REFRESH_TOKEN_INVALID, "The refresh token is invalid or has expired.");
  }
}
