package com.fooddelivery.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fooddelivery.identity.domain.Identifier;
import com.fooddelivery.identity.domain.RevokeReason;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.domain.UserStatus;
import com.fooddelivery.identity.infrastructure.persistence.RefreshTokenStore;
import com.fooddelivery.identity.infrastructure.persistence.RefreshTokenStore.StoredToken;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.identity.infrastructure.token.RefreshTokens;
import com.fooddelivery.platform.web.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** REQ-AUTH-001 AC4: rotation and family revocation on reuse. */
class TokenRefreshServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");
  private static final String TOKEN = "presented-token";
  private static final ClientContext CLIENT = new ClientContext("test-agent", "203.0.113.7");

  private final RefreshTokenStore store = mock(RefreshTokenStore.class);
  private final UserRepository users = mock(UserRepository.class);
  private final SessionTokenService sessions = mock(SessionTokenService.class);
  private final TokenRefreshService service =
      new TokenRefreshService(
          store, users, sessions, Transactions.direct(), Clock.fixed(NOW, ZoneOffset.UTC));

  private final UUID userId = UUID.randomUUID();
  private final UUID familyId = UUID.randomUUID();

  private StoredToken stored(Instant expiresAt, Instant rotatedAt, Instant revokedAt) {
    return new StoredToken(
        UUID.randomUUID(),
        userId,
        familyId,
        UUID.randomUUID(),
        "agent",
        expiresAt,
        rotatedAt,
        revokedAt);
  }

  private void givenStored(StoredToken token) {
    when(store.findForUpdate(RefreshTokens.hash(TOKEN))).thenReturn(Optional.of(token));
  }

  private void givenUser(UserStatus status) {
    User user = User.register(Identifier.email("a@example.com").orElseThrow(), null, "hash");
    ReflectionTestUtils.setField(user, "status", status);
    when(users.findById(userId)).thenReturn(Optional.of(user));
  }

  private static String code(Throwable thrown) {
    return ((ApiException) thrown).errorCode().code();
  }

  @Test
  void aValidTokenIsConsumedAndReplacedWithinTheSameFamily() {
    StoredToken token = stored(NOW.plus(Duration.ofDays(1)), null, null);
    givenStored(token);
    givenUser(UserStatus.ACTIVE);
    IssuedTokens next =
        new IssuedTokens(userId, token.sessionId(), "access", NOW, "next", NOW.plusSeconds(60));
    when(sessions.continueSession(token, CLIENT)).thenReturn(next);

    assertThat(service.refresh(TOKEN, CLIENT)).isSameAs(next);
    verify(store).markRotated(token.id(), NOW);
    verify(store, never()).revokeFamily(any(), any(), any());
  }

  @Test
  void presentingARotatedTokenAgainRevokesTheWholeFamily() {
    givenStored(stored(NOW.plus(Duration.ofDays(1)), NOW.minusSeconds(30), null));

    assertThatThrownBy(() -> service.refresh(TOKEN, CLIENT))
        .satisfies(e -> assertThat(code(e)).isEqualTo("REFRESH_TOKEN_REUSED"));
    verify(store).revokeFamily(familyId, NOW, RevokeReason.REUSE_DETECTED);
    verify(sessions, never()).continueSession(any(), any());
  }

  @Test
  void revokedExpiredAndUnknownTokensAreInvalid() {
    givenStored(stored(NOW.plus(Duration.ofDays(1)), null, NOW.minusSeconds(5)));
    assertThatThrownBy(() -> service.refresh(TOKEN, CLIENT))
        .satisfies(e -> assertThat(code(e)).isEqualTo("REFRESH_TOKEN_INVALID"));

    givenStored(stored(NOW, null, null));
    assertThatThrownBy(() -> service.refresh(TOKEN, CLIENT))
        .satisfies(e -> assertThat(code(e)).isEqualTo("REFRESH_TOKEN_INVALID"));

    when(store.findForUpdate(anyString())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refresh(TOKEN, CLIENT))
        .satisfies(e -> assertThat(code(e)).isEqualTo("REFRESH_TOKEN_INVALID"));

    verify(store, never()).markRotated(any(), any());
    verify(store, never()).revokeFamily(any(), any(), any());
  }

  @Test
  void aBlockedAccountLosesTheSession() {
    givenStored(stored(NOW.plus(Duration.ofDays(1)), null, null));
    givenUser(UserStatus.BLOCKED);

    assertThatThrownBy(() -> service.refresh(TOKEN, CLIENT))
        .satisfies(e -> assertThat(code(e)).isEqualTo("ACCOUNT_BLOCKED"));
    verify(store).revokeFamily(familyId, NOW, RevokeReason.BLOCKED);
    verify(store, never()).markRotated(any(), any());
  }

  @Test
  void oversizedInputIsRejectedWithoutTouchingTheDatabase() {
    assertThatThrownBy(() -> service.refresh("x".repeat(RefreshTokens.MAX_LENGTH + 1), CLIENT))
        .satisfies(e -> assertThat(code(e)).isEqualTo("REFRESH_TOKEN_INVALID"));
    assertThatThrownBy(() -> service.refresh(" ", CLIENT))
        .satisfies(e -> assertThat(code(e)).isEqualTo("REFRESH_TOKEN_INVALID"));
    verifyNoInteractions(store);
  }
}
