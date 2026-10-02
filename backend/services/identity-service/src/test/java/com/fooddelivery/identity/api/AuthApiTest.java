package com.fooddelivery.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.identity.application.AuthenticationService;
import com.fooddelivery.identity.application.ClientContext;
import com.fooddelivery.identity.application.IdentityErrorCode;
import com.fooddelivery.identity.application.IssuedTokens;
import com.fooddelivery.identity.application.RegistrationService;
import com.fooddelivery.identity.application.TokenRefreshService;
import com.fooddelivery.identity.config.SecurityConfig;
import com.fooddelivery.identity.infrastructure.persistence.SigningKeyStore;
import com.fooddelivery.platform.web.WebErrorAutoConfiguration;
import com.fooddelivery.platform.web.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract of the auth endpoints, with the application services mocked. */
@WebMvcTest(
    controllers = {AuthController.class, JwksController.class},
    properties = {
      "CONFIG_IMPORT=optional:file:../../../infrastructure/config-repo/application.yml",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false"
    })
@Import({SecurityConfig.class, AuthApiTest.FixedClock.class})
@ImportAutoConfiguration(WebErrorAutoConfiguration.class)
class AuthApiTest {

  static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean RegistrationService registration;
  @MockitoBean AuthenticationService authentication;
  @MockitoBean TokenRefreshService refresh;
  @MockitoBean SigningKeyStore signingKeys;

  private final UUID userId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  private IssuedTokens tokens() {
    return new IssuedTokens(
        userId,
        sessionId,
        "access.jwt",
        NOW.plusSeconds(900),
        "refresh-1",
        NOW.plusSeconds(2_592_000));
  }

  @Test
  void registrationReturns201WithAnUncacheableTokenPair() throws Exception {
    when(registration.register(any(), any())).thenReturn(tokens());

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "customer-web/1.0")
                .content(
                    """
                    {"fullName":"Asha Rao","email":"asha@example.com","password":"a long passphrase"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.accessToken").value("access.jwt"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.refreshToken").value("refresh-1"))
        .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

    verify(registration).register(any(), eq(new ClientContext("customer-web/1.0", "127.0.0.1")));
  }

  @Test
  void invalidCredentialsAreAProblemDocumentWithTheStableCode() throws Exception {
    when(authentication.login(anyString(), anyString(), any()))
        .thenThrow(
            new ApiException(
                IdentityErrorCode.INVALID_CREDENTIALS, "The identifier or password is incorrect."));

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"asha@example.com\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Content-Type", "application/problem+json"))
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("The identifier or password is incorrect."));
  }

  @Test
  void unknownPropertiesAndMissingFieldsAreValidationErrors() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"A","email":"a@example.com","password":"a long passphrase","roles":["ADMIN"]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));

    verify(registration, never()).register(any(), any());
    verify(refresh, never()).refresh(any(), any());
  }

  @Test
  void refreshReturnsTheRotatedPair() throws Exception {
    when(refresh.refresh(eq("refresh-0"), any())).thenReturn(tokens());

    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-0\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-1"));
  }

  @Test
  void theJwksIsPublicAndCacheable() throws Exception {
    when(signingKeys.publishedKeys())
        .thenReturn(List.of("{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\"AQAB\",\"e\":\"AQAB\"}"));

    mvc.perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=300, public"))
        .andExpect(jsonPath("$.keys[0].kid").value("k1"));
  }

  @Test
  void everythingElseIsDeniedByDefault() throws Exception {
    mvc.perform(get("/api/v1/auth/sessions"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
    mvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
  }
}
