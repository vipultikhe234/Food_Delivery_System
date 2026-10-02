package com.fooddelivery.identity.application;

import com.fooddelivery.identity.domain.Identifier;
import com.fooddelivery.identity.domain.PasswordPolicy;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.infrastructure.persistence.RoleStore;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.platform.events.EventEnvelope.Actor;
import com.fooddelivery.platform.events.outbox.NewEvent;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import com.fooddelivery.platform.web.error.ApiException;
import com.fooddelivery.platform.web.error.CommonErrorCode;
import com.fooddelivery.platform.web.error.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Customer self-registration (REQ-AUTH-001 AC1, AC2). */
@Service
public class RegistrationService {

  /** What the client submitted; the controller has already applied Bean Validation. */
  public record Registration(String fullName, String email, String phone, String password) {}

  static final String CUSTOMER_ROLE = "CUSTOMER";

  private final UserRepository users;
  private final RoleStore roles;
  private final SessionTokenService sessions;
  private final OutboxPublisher outbox;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate transactions;

  public RegistrationService(
      UserRepository users,
      RoleStore roles,
      SessionTokenService sessions,
      OutboxPublisher outbox,
      PasswordEncoder passwordEncoder,
      TransactionTemplate transactions) {
    this.users = users;
    this.roles = roles;
    this.sessions = sessions;
    this.outbox = outbox;
    this.passwordEncoder = passwordEncoder;
    this.transactions = transactions;
  }

  public IssuedTokens register(Registration registration, ClientContext client) {
    List<FieldViolation> violations = new ArrayList<>();
    Identifier email =
        identifier(
            registration.email(),
            Identifier::email,
            new FieldViolation("email", "INVALID", "must be a valid e-mail address"),
            violations);
    Identifier phone =
        identifier(
            registration.phone(),
            Identifier::phone,
            new FieldViolation("phone", "INVALID", "must be an E.164 number such as +919876543210"),
            violations);
    if (violations.isEmpty() && email == null && phone == null) {
      violations.add(
          new FieldViolation(
              "email", "REQUIRED", "an e-mail address or a phone number is required"));
    }
    if (!violations.isEmpty()) {
      throw new ApiException(
          CommonErrorCode.VALIDATION_FAILED, "The request is invalid.", violations, Map.of(), null);
    }
    PasswordPolicy.violation(registration.password())
        .ifPresent(
            message -> {
              throw new ApiException(
                  IdentityErrorCode.PASSWORD_POLICY_VIOLATION,
                  message,
                  List.of(new FieldViolation("password", "POLICY", message)),
                  Map.of(),
                  null);
            });

    // Hashing is deliberately slow; it runs before the transaction takes a connection.
    String passwordHash = passwordEncoder.encode(registration.password());
    String fullName = registration.fullName().strip();

    return transactions.execute(
        status -> {
          if ((email != null && users.existsByEmail(email.value()))
              || (phone != null && users.existsByPhone(phone.value()))) {
            throw alreadyExists(null);
          }
          User user = User.register(email, phone, passwordHash);
          try {
            users.saveAndFlush(user);
          } catch (DataIntegrityViolationException e) {
            if (isDuplicateIdentifier(e)) {
              throw alreadyExists(e);
            }
            throw e;
          }
          roles.assignGlobal(user.getId(), CUSTOMER_ROLE, null);
          outbox.publish(
              NewEvent.builder(UserRegistered.TOPIC, UserRegistered.TYPE, UserRegistered.VERSION)
                  .aggregate("User", user.getId(), user.getVersion())
                  .actor(Actor.user(user.getId()))
                  .payload(new UserRegistered(user.getId(), fullName, List.of(CUSTOMER_ROLE)))
                  .build());
          return sessions.startSession(user.getId(), client);
        });
  }

  private static Identifier identifier(
      String raw,
      Function<String, Optional<Identifier>> parser,
      FieldViolation invalid,
      List<FieldViolation> violations) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    Optional<Identifier> parsed = parser.apply(raw);
    if (parsed.isEmpty()) {
      violations.add(invalid);
      return null;
    }
    return parsed.get();
  }

  /** A concurrent registration won the race for the same e-mail address or phone number. */
  private static boolean isDuplicateIdentifier(DataIntegrityViolationException e) {
    String message = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
    return message.contains("uq_users_email") || message.contains("uq_users_phone");
  }

  /** The detail does not say which identifier matched. */
  private static ApiException alreadyExists(Throwable cause) {
    return new ApiException(
        IdentityErrorCode.USER_ALREADY_EXISTS,
        "An account with this e-mail address or phone number already exists.",
        cause);
  }
}
