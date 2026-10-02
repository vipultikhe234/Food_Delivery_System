package com.fooddelivery.platform.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a verified access token into a {@link UserAuthentication}. Every permission becomes an
 * authority of the same name, so endpoints declare {@code @PreAuthorize("hasAuthority('X')")};
 * roles become {@code ROLE_<role>}. A token with malformed claims is rejected with 401.
 */
public class UserJwtConverter implements Converter<Jwt, UserAuthentication> {

  static final String ROLE_PREFIX = "ROLE_";

  @Override
  public UserAuthentication convert(Jwt jwt) {
    AuthenticatedUser user;
    try {
      user =
          new AuthenticatedUser(
              UUID.fromString(jwt.getSubject()),
              UUID.fromString(jwt.getClaimAsString("sid")),
              Set.copyOf(strings(jwt, "roles")),
              Set.copyOf(strings(jwt, "perms")),
              scopes(jwt));
    } catch (RuntimeException e) {
      throw new InvalidBearerTokenException("The access token has malformed claims.", e);
    }
    List<GrantedAuthority> authorities = new ArrayList<>();
    user.permissions()
        .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
    user.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
    return new UserAuthentication(user, jwt, authorities);
  }

  private static List<String> strings(Jwt jwt, String claim) {
    List<String> values = jwt.getClaimAsStringList(claim);
    return values == null ? List.of() : values;
  }

  private static List<AccessScope> scopes(Jwt jwt) {
    Object claim = jwt.getClaims().get("scopes");
    if (claim == null) {
      return List.of();
    }
    List<AccessScope> scopes = new ArrayList<>();
    for (Object entry : (List<?>) claim) {
      Map<?, ?> scope = (Map<?, ?>) entry;
      scopes.add(
          new AccessScope(
              AccessScope.Type.valueOf(String.valueOf(scope.get("type"))),
              UUID.fromString(String.valueOf(scope.get("id")))));
    }
    return scopes;
  }
}
