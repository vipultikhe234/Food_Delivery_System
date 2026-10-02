package com.fooddelivery.identity.application;

import com.fooddelivery.identity.config.IdentityProperties;
import com.fooddelivery.identity.infrastructure.persistence.RefreshTokenStore;
import com.fooddelivery.identity.infrastructure.persistence.RefreshTokenStore.NewToken;
import com.fooddelivery.identity.infrastructure.persistence.RefreshTokenStore.StoredToken;
import com.fooddelivery.identity.infrastructure.persistence.RoleStore;
import com.fooddelivery.identity.infrastructure.persistence.RoleStore.Grants;
import com.fooddelivery.identity.infrastructure.token.AccessTokenIssuer;
import com.fooddelivery.identity.infrastructure.token.AccessTokenIssuer.AccessToken;
import com.fooddelivery.identity.infrastructure.token.RefreshTokens;
import com.fooddelivery.platform.persistence.id.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues token pairs. A session ({@code sid}) and its refresh-token family start together at login
 * and live until the family is revoked or expires. Callers provide the transaction.
 */
@Service
public class SessionTokenService {

  private final RoleStore roles;
  private final RefreshTokenStore refreshTokens;
  private final AccessTokenIssuer accessTokens;
  private final IdentityProperties properties;
  private final Clock clock;

  public SessionTokenService(
      RoleStore roles,
      RefreshTokenStore refreshTokens,
      AccessTokenIssuer accessTokens,
      IdentityProperties properties,
      Clock clock) {
    this.roles = roles;
    this.refreshTokens = refreshTokens;
    this.accessTokens = accessTokens;
    this.properties = properties;
    this.clock = clock;
  }

  public IssuedTokens startSession(UUID userId, ClientContext client) {
    return issue(userId, UuidV7.generate(), UuidV7.generate(), client);
  }

  /** The next token of a rotation chain: same family and session, new expiry. */
  public IssuedTokens continueSession(StoredToken previous, ClientContext client) {
    return issue(previous.userId(), previous.familyId(), previous.sessionId(), client);
  }

  private IssuedTokens issue(UUID userId, UUID familyId, UUID sessionId, ClientContext client) {
    Instant now = clock.instant();
    Grants grants = roles.grants(userId);
    AccessToken access = accessTokens.issue(userId, sessionId, grants, now);
    String refresh = RefreshTokens.generate();
    Instant refreshExpiresAt = now.plus(properties.tokens().refreshTokenTtl());
    refreshTokens.insert(
        new NewToken(
            UuidV7.generate(),
            userId,
            familyId,
            RefreshTokens.hash(refresh),
            sessionId,
            client.deviceInfo(),
            client.ip(),
            now,
            refreshExpiresAt));
    return new IssuedTokens(
        userId, sessionId, access.value(), access.expiresAt(), refresh, refreshExpiresAt);
  }
}
