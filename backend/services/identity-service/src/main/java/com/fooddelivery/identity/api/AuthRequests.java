package com.fooddelivery.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of {@code /api/v1/auth}. Unknown properties are rejected (docs/07 §1). */
final class AuthRequests {

  private AuthRequests() {}

  /**
   * At least one of e-mail and phone is required; the service reports which one is missing or
   * malformed. The password is checked against the policy there too, so the client gets {@code
   * PASSWORD_POLICY_VIOLATION} rather than a generic validation error.
   */
  record Register(
      @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}]*") String fullName,
      @Email @Size(max = 254) String email,
      @Size(max = 16) String phone,
      @NotNull String password) {}

  /** {@code identifier} is an e-mail address or an E.164 phone number. */
  record Login(
      @NotBlank @Size(max = 254) String identifier, @NotNull @Size(max = 1024) String password) {}

  record Refresh(@NotBlank @Size(max = 256) String refreshToken) {}
}
