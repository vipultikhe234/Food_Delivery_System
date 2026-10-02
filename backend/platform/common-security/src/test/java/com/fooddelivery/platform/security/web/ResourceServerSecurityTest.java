package com.fooddelivery.platform.security.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.platform.security.ResourceServerAutoConfiguration;
import com.fooddelivery.platform.security.TestTokens;
import com.fooddelivery.platform.web.WebErrorAutoConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SecurityTestApplication.SampleController.class)
@ImportAutoConfiguration({ResourceServerAutoConfiguration.class, WebErrorAutoConfiguration.class})
class ResourceServerSecurityTest {

  @Autowired MockMvc mvc;

  @Test
  void aPermittedCallerGetsThePrincipalFromTheToken() throws Exception {
    mvc.perform(get("/orders").header("Authorization", "Bearer customer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(TestTokens.USER.toString()))
        .andExpect(jsonPath("$.branches").value(1));
  }

  @Test
  void noTokenOrABadTokenIs401WithABearerChallenge() throws Exception {
    mvc.perform(get("/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
    mvc.perform(get("/orders").header("Authorization", "Bearer forged"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/orders").header("Authorization", "Bearer malformed"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aMissingPermissionIs403ProblemDetails() throws Exception {
    mvc.perform(post("/orders").header("Authorization", "Bearer partner"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void bearerWritesNeedNoCsrfTokenButCookieWritesDo() throws Exception {
    mvc.perform(post("/orders").header("Authorization", "Bearer customer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdBy").value(TestTokens.USER.toString()));
    mvc.perform(post("/public").cookie(new Cookie("session", "abc")))
        .andExpect(status().isForbidden());
  }

  @Test
  void permitAllEndpointsNeedNoToken() throws Exception {
    mvc.perform(get("/public")).andExpect(status().isOk());
  }
}
