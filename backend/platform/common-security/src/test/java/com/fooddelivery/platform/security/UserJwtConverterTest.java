package com.fooddelivery.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class UserJwtConverterTest {

  private final UserJwtConverter converter = new UserJwtConverter();

  @Test
  void claimsBecomeThePrincipalAndPermissionsBecomeAuthorities() {
    UserAuthentication authentication =
        converter.convert(
            TestTokens.token(List.of("RESTAURANT_MANAGER"), List.of("ORDER_VIEW", "MENU_UPDATE"))
                .build());

    AuthenticatedUser user = authentication.getPrincipal();
    assertThat(user.userId()).isEqualTo(TestTokens.USER);
    assertThat(user.sessionId()).isEqualTo(TestTokens.SESSION);
    assertThat(user.hasRole("RESTAURANT_MANAGER")).isTrue();
    assertThat(user.hasPermission("MENU_UPDATE")).isTrue();
    assertThat(user.inScope(AccessScope.Type.BRANCH, TestTokens.BRANCH)).isTrue();
    assertThat(user.inScope(AccessScope.Type.RESTAURANT, TestTokens.BRANCH)).isFalse();
    assertThat(user.scopeIds(AccessScope.Type.RESTAURANT)).containsExactly(TestTokens.RESTAURANT);
    assertThat(authentication.getName()).isEqualTo(TestTokens.USER.toString());
    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ORDER_VIEW", "MENU_UPDATE", "ROLE_RESTAURANT_MANAGER");
  }

  @Test
  void missingListClaimsMeanNoGrants() {
    Jwt jwt =
        TestTokens.token(List.of(), List.of())
            .claims(
                claims -> {
                  claims.remove("roles");
                  claims.remove("perms");
                  claims.remove("scopes");
                })
            .build();

    AuthenticatedUser user = converter.convert(jwt).getPrincipal();

    assertThat(user.roles()).isEmpty();
    assertThat(user.permissions()).isEmpty();
    assertThat(user.scopes()).isEmpty();
  }

  @Test
  void malformedClaimsAreRejectedAsAnInvalidToken() {
    Jwt notAUuid = TestTokens.token(List.of(), List.of()).subject("device:42").build();
    Jwt noSession = TestTokens.token(List.of(), List.of()).claims(c -> c.remove("sid")).build();
    Jwt unknownScope =
        TestTokens.token(List.of(), List.of())
            .claim("scopes", List.of(Map.of("type", "PLANET", "id", TestTokens.BRANCH.toString())))
            .build();

    for (Jwt jwt : List.of(notAUuid, noSession, unknownScope)) {
      assertThatThrownBy(() -> converter.convert(jwt))
          .isInstanceOf(InvalidBearerTokenException.class)
          .hasMessage("The access token has malformed claims.");
    }
  }
}
