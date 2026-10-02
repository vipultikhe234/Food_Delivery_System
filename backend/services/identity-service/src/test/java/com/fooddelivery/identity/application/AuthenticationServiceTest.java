package com.fooddelivery.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fooddelivery.identity.domain.Identifier;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.domain.UserStatus;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.platform.web.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** REQ-AUTH-001 AC3: login, and responses that do not reveal whether an account exists. */
class AuthenticationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");
  private static final ClientContext CLIENT = new ClientContext("agent", "203.0.113.7");

  private final UserRepository users = mock(UserRepository.class);
  private final SessionTokenService sessions = mock(SessionTokenService.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private AuthenticationService service;
  private User user;

  @BeforeEach
  void setUp() {
    when(encoder.encode(anyString())).thenReturn("dummy-hash");
    service =
        new AuthenticationService(
            users, sessions, encoder, Transactions.direct(), Clock.fixed(NOW, ZoneOffset.UTC));
    user = User.register(Identifier.email("asha@example.com").orElseThrow(), null, "stored-hash");
    when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
  }

  @Test
  void correctCredentialsStartASession() {
    when(encoder.matches("right password", "stored-hash")).thenReturn(true);
    IssuedTokens tokens = new IssuedTokens(user.getId(), null, "a", NOW, "r", NOW);
    when(sessions.startSession(user.getId(), CLIENT)).thenReturn(tokens);

    assertThat(service.login("  Asha@Example.com", "right password", CLIENT)).isSameAs(tokens);
    verify(users).recordLogin(user.getId(), NOW);
    verify(users, never()).rehashPassword(any(), any(), any());
  }

  @Test
  void unknownAccountsAndWrongPasswordsGetTheSameAnswerAndTheSameWork() {
    ApiException wrongPassword =
        catchThrowableOfType(
            ApiException.class, () -> service.login("asha@example.com", "wrong", CLIENT));
    ApiException unknown =
        catchThrowableOfType(
            ApiException.class, () -> service.login("nobody@example.com", "wrong", CLIENT));
    ApiException malformed =
        catchThrowableOfType(ApiException.class, () -> service.login("nobody", "wrong", CLIENT));

    for (ApiException e : new ApiException[] {wrongPassword, unknown, malformed}) {
      assertThat(e.errorCode().code()).isEqualTo("INVALID_CREDENTIALS");
      assertThat(e.detail()).isEqualTo(wrongPassword.detail());
    }
    verify(encoder).matches("wrong", "stored-hash");
    verify(encoder, times(2)).matches("wrong", "dummy-hash");
    verify(sessions, never()).startSession(any(), any());
  }

  @Test
  void deletedAccountsCannotLogIn() {
    ReflectionTestUtils.setField(user, "status", UserStatus.DELETED);

    ApiException e =
        catchThrowableOfType(
            ApiException.class, () -> service.login("asha@example.com", "right password", CLIENT));

    assertThat(e.errorCode().code()).isEqualTo("INVALID_CREDENTIALS");
    verify(encoder).matches(eq("right password"), eq("dummy-hash"));
  }

  @Test
  void theAccountStateIsRevealedOnlyAfterTheRightPassword() {
    ReflectionTestUtils.setField(user, "status", UserStatus.BLOCKED);
    when(encoder.matches("right password", "stored-hash")).thenReturn(true);

    assertThat(
            catchThrowableOfType(
                    ApiException.class, () -> service.login("asha@example.com", "wrong", CLIENT))
                .errorCode()
                .code())
        .isEqualTo("INVALID_CREDENTIALS");
    assertThat(
            catchThrowableOfType(
                    ApiException.class,
                    () -> service.login("asha@example.com", "right password", CLIENT))
                .errorCode()
                .code())
        .isEqualTo("ACCOUNT_BLOCKED");
  }

  @Test
  void aLockedAccountGetsRetryAfter() {
    ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);
    ReflectionTestUtils.setField(user, "lockedUntil", NOW.plusSeconds(600));
    when(encoder.matches("right password", "stored-hash")).thenReturn(true);

    ApiException e =
        catchThrowableOfType(
            ApiException.class, () -> service.login("asha@example.com", "right password", CLIENT));

    assertThat(e.errorCode().code()).isEqualTo("ACCOUNT_LOCKED");
    assertThat(e.errorCode().status().value()).isEqualTo(423);
    assertThat(e.headers()).containsEntry("Retry-After", "600");
  }

  @Test
  void outdatedHashesAreUpgradedOnLogin() {
    when(encoder.matches("right password", "stored-hash")).thenReturn(true);
    when(encoder.upgradeEncoding("stored-hash")).thenReturn(true);
    when(encoder.encode("right password")).thenReturn("new-hash");

    service.login("asha@example.com", "right password", CLIENT);

    verify(users).rehashPassword(user.getId(), "new-hash", NOW);
  }
}
