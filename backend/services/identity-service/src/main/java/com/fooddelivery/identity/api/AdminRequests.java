package com.fooddelivery.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request bodies of the access administration endpoints. Every change carries a reason for the
 * audit record (docs/09 §2.4).
 */
final class AdminRequests {

  static final String CODE = "[A-Z][A-Z_]{1,63}";
  static final String NO_CONTROL_CHARACTERS = "[^\\p{Cntrl}]*";

  private AdminRequests() {}

  record ChangeRolePermissions(
      @NotNull @Size(max = 200) List<@NotBlank @Pattern(regexp = CODE) String> permissions,
      @NotBlank @Size(max = 500) @Pattern(regexp = NO_CONTROL_CHARACTERS) String reason) {}

  /** {@code scopeType} defaults to GLOBAL; RESTAURANT and BRANCH need a {@code scopeId}. */
  record GrantRole(
      @NotBlank @Pattern(regexp = CODE) String role,
      @Pattern(regexp = "GLOBAL|RESTAURANT|BRANCH") String scopeType,
      UUID scopeId,
      @NotBlank @Size(max = 500) @Pattern(regexp = NO_CONTROL_CHARACTERS) String reason) {}
}
