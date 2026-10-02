package com.fooddelivery.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fooddelivery.identity.application.RegistrationService.Registration;
import com.fooddelivery.identity.domain.User;
import com.fooddelivery.identity.domain.UserStatus;
import com.fooddelivery.identity.infrastructure.persistence.RoleStore;
import com.fooddelivery.identity.infrastructure.persistence.UserRepository;
import com.fooddelivery.platform.events.outbox.NewEvent;
import com.fooddelivery.platform.events.outbox.OutboxPublisher;
import com.fooddelivery.platform.web.error.ApiException;
import com.fooddelivery.platform.web.error.FieldViolation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** REQ-AUTH-001 AC1 and AC2. */
class RegistrationServiceTest {

  private static final ClientContext CLIENT = new ClientContext("agent", "203.0.113.7");
  private static final String PASSWORD = "a long enough passphrase";

  private final UserRepository users = mock(UserRepository.class);
  private final RoleStore roles = mock(RoleStore.class);
  private final SessionTokenService sessions = mock(SessionTokenService.class);
  private final OutboxPublisher outbox = mock(OutboxPublisher.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final RegistrationService service =
      new RegistrationService(users, roles, sessions, outbox, encoder, Transactions.direct());

  @BeforeEach
  void setUp() {
    when(encoder.encode(PASSWORD)).thenReturn("$argon2id$hash");
  }

  private static ApiException failure(Runnable call) {
    return catchThrowableOfType(ApiException.class, call::run);
  }

  @Test
  void aCustomerIsCreatedActiveWithAHashedPasswordTheCustomerRoleAndAnEvent() {
    IssuedTokens tokens = new IssuedTokens(null, null, "a", null, "r", null);
    when(sessions.startSession(any(), any())).thenReturn(tokens);

    IssuedTokens result =
        service.register(
            new Registration("  Asha Rao ", " Asha@Example.com ", "+919876543210", PASSWORD),
            CLIENT);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).saveAndFlush(saved.capture());
    User user = saved.getValue();
    assertThat(user.getEmail()).isEqualTo("asha@example.com");
    assertThat(user.getPhone()).isEqualTo("+919876543210");
    assertThat(user.getPasswordHash()).isEqualTo("$argon2id$hash").isNotEqualTo(PASSWORD);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getPhoneVerifiedAt()).isNull();
    verify(roles).assignGlobal(user.getId(), "CUSTOMER", null);

    ArgumentCaptor<NewEvent> event = ArgumentCaptor.forClass(NewEvent.class);
    verify(outbox).publish(event.capture());
    assertThat(event.getValue().topic()).isEqualTo("identity.events.v1");
    assertThat(event.getValue().eventType()).isEqualTo("UserRegistered");
    assertThat(event.getValue().partitionKey()).isEqualTo(user.getId().toString());
    assertThat(event.getValue().payload())
        .isEqualTo(new UserRegistered(user.getId(), "Asha Rao", List.of("CUSTOMER")));

    verify(sessions).startSession(user.getId(), CLIENT);
    assertThat(result).isSameAs(tokens);
  }

  @Test
  void anEmailOrAPhoneIsRequiredAndMustBeWellFormed() {
    ApiException neither =
        failure(() -> service.register(new Registration("A", null, " ", PASSWORD), CLIENT));
    assertThat(neither.errorCode().code()).isEqualTo("VALIDATION_FAILED");
    assertThat(neither.errors()).extracting(FieldViolation::code).containsExactly("REQUIRED");

    ApiException badPhone =
        failure(() -> service.register(new Registration("A", null, "98765", PASSWORD), CLIENT));
    assertThat(badPhone.errors()).extracting(FieldViolation::field).containsExactly("phone");

    verify(encoder, never()).encode(anyString());
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void aWeakPasswordIsRejectedWithItsOwnCode() {
    ApiException e =
        failure(
            () -> service.register(new Registration("A", "a@example.com", null, "short"), CLIENT));

    assertThat(e.errorCode().code()).isEqualTo("PASSWORD_POLICY_VIOLATION");
    assertThat(e.errors()).extracting(FieldViolation::field).containsExactly("password");
    verify(encoder, never()).encode(anyString());
  }

  @Test
  void anExistingEmailOrPhoneIsAConflictThatDoesNotSayWhichMatched() {
    when(users.existsByPhone("+919876543210")).thenReturn(true);

    ApiException e =
        failure(
            () ->
                service.register(
                    new Registration("A", "new@example.com", "+919876543210", PASSWORD), CLIENT));

    assertThat(e.errorCode().code()).isEqualTo("USER_ALREADY_EXISTS");
    assertThat(e.errorCode().status().value()).isEqualTo(409);
    assertThat(e.detail()).contains("e-mail address or phone number");
    verify(users, never()).saveAndFlush(any());
    verify(outbox, never()).publish(any());
  }

  @Test
  void losingARegistrationRaceIsTheSameConflict() {
    when(users.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate",
                new RuntimeException(
                    "ERROR: duplicate key value violates unique constraint \"uq_users_email\"")));

    ApiException e =
        failure(
            () -> service.register(new Registration("A", "a@example.com", null, PASSWORD), CLIENT));

    assertThat(e.errorCode().code()).isEqualTo("USER_ALREADY_EXISTS");
    verify(roles, never()).assignGlobal(any(), anyString(), isNull());
  }
}
