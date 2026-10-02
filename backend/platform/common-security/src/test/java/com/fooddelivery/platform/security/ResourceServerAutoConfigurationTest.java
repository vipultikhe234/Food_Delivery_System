package com.fooddelivery.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class ResourceServerAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ResourceServerAutoConfiguration.class))
          .withPropertyValues(
              "fdp.security.jwt.issuer=fooddelivery-identity",
              "fdp.security.jwt.audience=fooddelivery-api");

  @Test
  void aJwksUriGivesAValidatingDecoder() {
    runner
        .withPropertyValues(
            "fdp.security.jwt.jwks-uri=http://identity-service/.well-known/jwks.json")
        .run(
            context -> {
              assertThat(context).hasSingleBean(JwtDecoder.class);
              assertThat(context).hasSingleBean(UserJwtConverter.class);
            });
  }

  @Test
  void withoutAJwksUriTheServiceMustProvideItsOwnDecoder() {
    runner.run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
  }

  @Test
  void csrfIsSkippedOnlyForBearerRequestsOrRequestsWithoutCookies() {
    MockHttpServletRequest bearer = new MockHttpServletRequest("POST", "/x");
    bearer.addHeader("Authorization", "Bearer abc");
    bearer.addHeader("Cookie", "a=b");
    MockHttpServletRequest noCookies = new MockHttpServletRequest("POST", "/x");
    MockHttpServletRequest cookieOnly = new MockHttpServletRequest("POST", "/x");
    cookieOnly.addHeader("Cookie", "session=abc");
    MockHttpServletRequest basicWithCookie = new MockHttpServletRequest("POST", "/x");
    basicWithCookie.addHeader("Authorization", "Basic abc");
    basicWithCookie.addHeader("Cookie", "session=abc");

    assertThat(ResourceServerSecurity.withoutAmbientCredentials(bearer)).isTrue();
    assertThat(ResourceServerSecurity.withoutAmbientCredentials(noCookies)).isTrue();
    assertThat(ResourceServerSecurity.withoutAmbientCredentials(cookieOnly)).isFalse();
    assertThat(ResourceServerSecurity.withoutAmbientCredentials(basicWithCookie)).isFalse();
  }
}
