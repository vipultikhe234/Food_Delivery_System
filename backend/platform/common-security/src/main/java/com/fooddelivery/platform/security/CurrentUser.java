package com.fooddelivery.platform.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The authenticated caller of the current request, for application services. Controllers can take
 * {@code @AuthenticationPrincipal AuthenticatedUser} instead.
 */
public final class CurrentUser {

  private CurrentUser() {}

  public static AuthenticatedUser get() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      return user;
    }
    throw new AuthenticationCredentialsNotFoundException("No authenticated user");
  }
}
