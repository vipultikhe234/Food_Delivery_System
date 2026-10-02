package com.fooddelivery.platform.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** A request authenticated by an access token; the principal is an {@link AuthenticatedUser}. */
public final class UserAuthentication extends AbstractAuthenticationToken {

  private final AuthenticatedUser user;
  private final transient Jwt token;

  public UserAuthentication(
      AuthenticatedUser user, Jwt token, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.user = user;
    this.token = token;
    setAuthenticated(true);
  }

  @Override
  public AuthenticatedUser getPrincipal() {
    return user;
  }

  @Override
  public Jwt getCredentials() {
    return token;
  }

  @Override
  public String getName() {
    return user.userId().toString();
  }
}
