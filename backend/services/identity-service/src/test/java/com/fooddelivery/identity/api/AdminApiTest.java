package com.fooddelivery.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.identity.application.AccessAdministrationService;
import com.fooddelivery.identity.application.AccessAdministrationService.Caller;
import com.fooddelivery.identity.application.AccessAdministrationService.RoleGrant;
import com.fooddelivery.identity.config.SecurityConfig;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore.Permission;
import com.fooddelivery.identity.infrastructure.persistence.RoleCatalogStore.Role;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Assignment;
import com.fooddelivery.identity.infrastructure.persistence.UserRoleStore.Granted;
import com.fooddelivery.platform.security.ResourceServerAutoConfiguration;
import com.fooddelivery.platform.web.WebErrorAutoConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Permission matrix of the access administration endpoints (REQ-AUTH-003 AC1, AC3, AC5): no token
 * is 401, a token without the permission is 403 before any service call, the right permission
 * reaches the service.
 */
@WebMvcTest(
    controllers = AccessAdminController.class,
    properties = {
      "CONFIG_IMPORT=optional:file:../../../infrastructure/config-repo/application.yml",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false"
    })
@Import(SecurityConfig.class)
@ImportAutoConfiguration({WebErrorAutoConfiguration.class, ResourceServerAutoConfiguration.class})
class AdminApiTest {

  static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");
  static final String SUPER_ADMIN = "super-admin";
  static final String CUSTOMER = "customer";
  static final String ADMIN = "admin";

  @Autowired MockMvc mvc;
  @MockitoBean AccessAdministrationService administration;
  @MockitoBean JwtDecoder jwtDecoder;

  private final UUID userId = UUID.randomUUID();
  private final UUID assignmentId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();

  @BeforeEach
  void tokens() {
    Map<String, Jwt> tokens =
        Map.of(
            SUPER_ADMIN,
            jwt(SUPER_ADMIN, List.of("SUPER_ADMIN"), List.of("ROLE_MANAGE", "PERMISSION_MANAGE")),
            ADMIN,
            jwt(ADMIN, List.of("ADMIN"), List.of("USER_BLOCK", "AUDIT_LOG_VIEW")),
            CUSTOMER,
            jwt(CUSTOMER, List.of("CUSTOMER"), List.of("ORDER_PLACE")));
    when(jwtDecoder.decode(anyString()))
        .thenAnswer(
            invocation -> {
              Jwt jwt = tokens.get(invocation.<String>getArgument(0));
              if (jwt == null) {
                throw new BadJwtException("bad token");
              }
              return jwt;
            });
  }

  private List<MockHttpServletRequestBuilder> everyEndpoint() {
    return List.of(
        get("/api/v1/admin/permissions"),
        get("/api/v1/admin/roles"),
        put("/api/v1/admin/roles/CASHIER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"permissions\":[\"POS_ORDER\"],\"reason\":\"r\"}"),
        get("/api/v1/admin/users/{userId}/roles", userId),
        post("/api/v1/admin/users/{userId}/roles", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"CASHIER\",\"reason\":\"r\"}"),
        delete("/api/v1/admin/users/{userId}/roles/{id}", userId, assignmentId)
            .param("reason", "r"));
  }

  @Test
  void withoutATokenEveryEndpointIs401() throws Exception {
    for (MockHttpServletRequestBuilder request : everyEndpoint()) {
      mvc.perform(request)
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }
    verifyNoInteractions(administration);
  }

  @Test
  void anInvalidTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/admin/roles").header("Authorization", "Bearer forged"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(administration);
  }

  @Test
  void withoutThePermissionEveryEndpointIs403() throws Exception {
    for (String token : List.of(CUSTOMER, ADMIN)) {
      for (MockHttpServletRequestBuilder request : everyEndpoint()) {
        mvc.perform(request.header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
      }
    }
    verifyNoInteractions(administration);
  }

  @Test
  void aSuperAdminListsPermissionsAndRoles() throws Exception {
    when(administration.permissions())
        .thenReturn(List.of(new Permission("ROLE_MANAGE", "ADMIN", "Grant and revoke roles")));
    when(administration.roles())
        .thenReturn(
            List.of(
                new Role(
                    UUID.randomUUID(), "CASHIER", "d", Set.of("BRANCH"), List.of("POS_ORDER"))));

    mvc.perform(get("/api/v1/admin/permissions").header("Authorization", "Bearer " + SUPER_ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("ROLE_MANAGE"))
        .andExpect(jsonPath("$[0].category").value("ADMIN"));
    mvc.perform(get("/api/v1/admin/roles").header("Authorization", "Bearer " + SUPER_ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("CASHIER"))
        .andExpect(jsonPath("$[0].scopeTypes[0]").value("BRANCH"))
        .andExpect(jsonPath("$[0].permissions[0]").value("POS_ORDER"));
  }

  @Test
  void aNewGrantIs201AndARepeatedOneIs200() throws Exception {
    Assignment assignment =
        new Assignment(assignmentId, userId, "CASHIER", "BRANCH", UUID.randomUUID(), adminId, NOW);
    when(administration.grant(any(), eq(userId), any()))
        .thenReturn(new Granted(assignment, true), new Granted(assignment, false));
    String body =
        """
        {"role":"CASHIER","scopeType":"BRANCH","scopeId":"%s","reason":"weekend rota"}
        """
            .formatted(assignment.scopeId());

    mvc.perform(
            post("/api/v1/admin/users/{userId}/roles", userId)
                .header("Authorization", "Bearer " + SUPER_ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(assignmentId.toString()))
        .andExpect(jsonPath("$.role").value("CASHIER"))
        .andExpect(jsonPath("$.scopeId").value(assignment.scopeId().toString()));
    mvc.perform(
            post("/api/v1/admin/users/{userId}/roles", userId)
                .header("Authorization", "Bearer " + SUPER_ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    ArgumentCaptor<RoleGrant> grant = ArgumentCaptor.forClass(RoleGrant.class);
    verify(administration, times(2)).grant(caller.capture(), eq(userId), grant.capture());
    assertThat(caller.getValue().user().userId()).isEqualTo(adminId);
    assertThat(caller.getValue().ip()).isEqualTo("127.0.0.1");
    assertThat(grant.getValue())
        .isEqualTo(new RoleGrant("CASHIER", "BRANCH", assignment.scopeId(), "weekend rota"));
  }

  @Test
  void rolePermissionsAreReplaced() throws Exception {
    when(administration.changeRolePermissions(any(), eq("CASHIER"), anyCollection(), anyString()))
        .thenReturn(
            new Role(
                UUID.randomUUID(),
                "CASHIER",
                "d",
                Set.of("BRANCH"),
                List.of("BILL_VIEW", "POS_ORDER")));

    mvc.perform(
            put("/api/v1/admin/roles/CASHIER")
                .header("Authorization", "Bearer " + SUPER_ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"permissions\":[\"POS_ORDER\",\"BILL_VIEW\"],\"reason\":\"reprint bills\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(2));

    verify(administration)
        .changeRolePermissions(
            any(), eq("CASHIER"), eq(List.of("POS_ORDER", "BILL_VIEW")), eq("reprint bills"));
  }

  @Test
  void aRevocationIs204() throws Exception {
    mvc.perform(
            delete("/api/v1/admin/users/{userId}/roles/{id}", userId, assignmentId)
                .param("reason", "left the outlet")
                .header("Authorization", "Bearer " + SUPER_ADMIN))
        .andExpect(status().isNoContent());

    verify(administration).revoke(any(), eq(userId), eq(assignmentId), eq("left the outlet"));
  }

  @Test
  void invalidRequestsAre400WithoutReachingTheService() throws Exception {
    String auth = "Bearer " + SUPER_ADMIN;
    mvc.perform(
            delete("/api/v1/admin/users/{userId}/roles/{id}", userId, assignmentId)
                .header("Authorization", auth))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/admin/users/{userId}/roles", userId)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"CASHIER\",\"scopeType\":\"PLANET\",\"reason\":\"r\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("scopeType"));
    mvc.perform(
            post("/api/v1/admin/users/{userId}/roles", userId)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"CASHIER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("reason"));
    mvc.perform(
            put("/api/v1/admin/roles/cashier")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[],\"reason\":\"r\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/v1/admin/roles/CASHIER")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"pos order\"],\"reason\":\"r\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/admin/users/not-a-uuid/roles").header("Authorization", auth))
        .andExpect(status().isBadRequest());

    verify(administration, never()).grant(any(), any(), any());
    verify(administration, never()).revoke(any(), any(), any(), any());
    verify(administration, never()).changeRolePermissions(any(), any(), any(), any());
  }

  private Jwt jwt(String value, List<String> roles, List<String> permissions) {
    return Jwt.withTokenValue(value)
        .header("alg", "RS256")
        .issuer("fooddelivery-identity")
        .audience(List.of("fooddelivery-api"))
        .subject(adminId.toString())
        .issuedAt(NOW)
        .expiresAt(NOW.plusSeconds(900))
        .claim("sid", UUID.randomUUID().toString())
        .claim("roles", roles)
        .claim("perms", permissions)
        .claim("scopes", List.of())
        .build();
  }
}
