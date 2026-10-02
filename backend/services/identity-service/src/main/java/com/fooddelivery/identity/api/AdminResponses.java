package com.fooddelivery.identity.api;

import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class AdminResponses {

  private AdminResponses() {}

  record PermissionResponse(String code, String category, String description) {
    static PermissionResponse of(RoleCatalogStore.Permission permission) {
      return new PermissionResponse(
          permission.code(), permission.category(), permission.description());
    }
  }

  record RoleResponse(
      String code, String description, List<String> scopeTypes, List<String> permissions) {
    static RoleResponse of(RoleCatalogStore.Role role) {
      return new RoleResponse(
          role.code(), role.description(), List.copyOf(role.scopeTypes()), role.permissions());
    }
  }

  record AssignmentResponse(
      UUID id, String role, String scopeType, UUID scopeId, UUID grantedBy, Instant grantedAt) {
    static AssignmentResponse of(UserRoleStore.Assignment assignment) {
      return new AssignmentResponse(
          assignment.id(),
          assignment.role(),
          assignment.scopeType(),
          assignment.scopeId(),
          assignment.grantedBy(),
          assignment.grantedAt());
    }
  }
}
