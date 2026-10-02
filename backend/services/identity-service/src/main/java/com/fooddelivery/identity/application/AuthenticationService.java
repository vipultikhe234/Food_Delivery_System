package com.fooddelivery.identity.application;

import com.fooddelivery.identity.domain.Identifier;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.domain.UserStatus;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.platform.web.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Password login (REQ-AUTH-001 AC3). Unknown identifiers and wrong passwords get the same response,
 * and an unknown identifier still costs one hash comparison, so neither the body nor the timing
 * reveals whether an account exists (docs/09-security.md §2.1).
 */
@Service
public class AuthenticationService {

  private final UserRepository users;
  private final SessionTokenService sessions;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final String dummyHash;

  public AuthenticationService(
      UserRepository users,
      SessionTokenService sessions,
      PasswordEncoder passwordEncoder,
      TransactionTemplate transactions,
      Clock clock) {
    this.users = users;
    this.sessions = sessions;
    this.passwordEncoder = passwordEncoder;
    this.transactions = transactions;
    this.clock = clock;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  public IssuedTokens login(String identifier, String password, ClientContext client) {
    User user =
        Identifier.parse(identifier)
            .flatMap(this::lookup)
            .filter(u -> u.getPasswordHash() != null && u.getStatus() != UserStatus.DELETED)
            .orElse(null);
    if (user == null) {
      passwordEncoder.matches(password, dummyHash);
      throw invalidCredentials();
    }
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw invalidCredentials();
    }
    // Only a caller who knows the password learns about the account state.
    if (user.getStatus() == UserStatus.BLOCKED) {
      throw new ApiException(
          IdentityErrorCode.ACCOUNT_BLOCKED, "This account has been blocked. Contact support.");
    }
    if (user.getStatus() == UserStatus.LOCKED) {
      throw locked(user.getLockedUntil());
    }
    String rehash =
        passwordEncoder.upgradeEncoding(user.getPasswordHash())
            ? passwordEncoder.encode(password)
            : null;

    return transactions.execute(
        status -> {
          Instant now = clock.instant();
          users.recordLogin(user.getId(), now);
          if (rehash != null) {
            users.rehashPassword(user.getId(), rehash, now);
          }
          return sessions.startSession(user.getId(), client);
        });
  }

  private Optional<User> lookup(Identifier identifier) {
    return switch (identifier.type()) {
      case EMAIL -> users.findByEmail(identifier.value());
      case PHONE -> users.findByPhone(identifier.value());
    };
  }

  private ApiException locked(Instant lockedUntil) {
    Map<String, String> headers = Map.of();
    if (lockedUntil != null) {
      long seconds = Duration.between(clock.instant(), lockedUntil).toSeconds();
      if (seconds > 0) {
        headers = Map.of("Retry-After", Long.toString(seconds));
      }
    }
    return new ApiException(
        IdentityErrorCode.ACCOUNT_LOCKED,
        "This account is temporarily locked. Try again later.",
        List.of(),
        headers,
        null);
  }

  private static ApiException invalidCredentials() {
    return new ApiException(
        IdentityErrorCode.INVALID_CREDENTIALS, "The identifier or password is incorrect.");
  }
}
