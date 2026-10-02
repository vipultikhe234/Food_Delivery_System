package com.fooddelivery.identity.api;

import com.fooddelivery.identity.api.AdminResponses.AssignmentResponse;
import com.fooddelivery.identity.api.AdminResponses.PermissionResponse;
import com.fooddelivery.identity.api.AdminResponses.RoleResponse;
import com.fooddelivery.identity.application.AccessAdministrationService;
import com.fooddelivery.identity.application.AccessAdministrationService.Caller;
import com.fooddelivery.identity.application.AccessAdministrationService.RoleGrant;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Granted;
import com.fooddelivery.platform.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles, permissions and role assignments (REQ-AUTH-003 AC2, AC5; docs/05 §1.2 identity-admin
 * routes). ROLE_MANAGE and PERMISSION_MANAGE belong to SUPER_ADMIN only (docs/09 §3.3).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AccessAdminController {

  private final AccessAdministrationService administration;

  AccessAdminController(AccessAdministrationService administration) {
    this.administration = administration;
  }

  @GetMapping("/permissions")
  @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
  List<PermissionResponse> permissions() {
    return administration.permissions().stream().map(PermissionResponse::of).toList();
  }

  @GetMapping("/roles")
  @PreAuthorize("hasAuthority('ROLE_MANAGE')")
  List<RoleResponse> roles() {
    return administration.roles().stream().map(RoleResponse::of).toList();
  }

  /** Replaces the permission set of a role. */
  @PutMapping("/roles/{code}")
  @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
  RoleResponse changeRolePermissions(
      @PathVariable @Pattern(regexp = AdminRequests.CODE) String code,
      @Valid @RequestBody AdminRequests.ChangeRolePermissions body,
      @AuthenticationPrincipal AuthenticatedUser user,
      HttpServletRequest request) {
    return RoleResponse.of(
        administration.changeRolePermissions(
            caller(user, request), code, body.permissions(), body.reason()));
  }

  @GetMapping("/users/{userId}/roles")
  @PreAuthorize("hasAuthority('ROLE_MANAGE')")
  List<AssignmentResponse> assignments(@PathVariable UUID userId) {
    return administration.assignments(userId).stream().map(AssignmentResponse::of).toList();
  }

  /** 201 for a new assignment, 200 when the user already holds the role with that scope. */
  @PostMapping("/users/{userId}/roles")
  @PreAuthorize("hasAuthority('ROLE_MANAGE')")
  ResponseEntity<AssignmentResponse> grant(
      @PathVariable UUID userId,
      @Valid @RequestBody AdminRequests.GrantRole body,
      @AuthenticationPrincipal AuthenticatedUser user,
      HttpServletRequest request) {
    Granted granted =
        administration.grant(
            caller(user, request),
            userId,
            new RoleGrant(body.role(), body.scopeType(), body.scopeId(), body.reason()));
    return ResponseEntity.status(granted.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(AssignmentResponse.of(granted.assignment()));
  }

  @DeleteMapping("/users/{userId}/roles/{assignmentId}")
  @PreAuthorize("hasAuthority('ROLE_MANAGE')")
  ResponseEntity<Void> revoke(
      @PathVariable UUID userId,
      @PathVariable UUID assignmentId,
      @RequestParam
          @NotBlank
          @Size(max = 500)
          @Pattern(regexp = AdminRequests.NO_CONTROL_CHARACTERS)
          String reason,
      @AuthenticationPrincipal AuthenticatedUser user,
      HttpServletRequest request) {
    administration.revoke(caller(user, request), userId, assignmentId, reason);
    return ResponseEntity.noContent().build();
  }

  private static Caller caller(AuthenticatedUser user, HttpServletRequest request) {
    return new Caller(user, request.getRemoteAddr());
  }
}
