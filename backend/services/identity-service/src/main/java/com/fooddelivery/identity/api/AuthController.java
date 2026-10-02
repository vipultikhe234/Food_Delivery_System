package com.fooddelivery.identity.api;

import com.fooddelivery.identity.application.AuthenticationService;
import com.fooddelivery.identity.application.ClientContext;
import com.fooddelivery.identity.application.IssuedTokens;
import com.fooddelivery.identity.application.RegistrationService;
import com.fooddelivery.identity.application.RegistrationService.Registration;
import com.fooddelivery.identity.application.TokenRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login and refresh (REQ-AUTH-001). Tokens travel in the response body; the web
 * cookie variant with CSRF protection is not built yet (KI-029).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

  private final RegistrationService registration;
  private final AuthenticationService authentication;
  private final TokenRefreshService refresh;
  private final Clock clock;

  AuthController(
      RegistrationService registration,
      AuthenticationService authentication,
      TokenRefreshService refresh,
      Clock clock) {
    this.registration = registration;
    this.authentication = authentication;
    this.refresh = refresh;
    this.clock = clock;
  }

  @PostMapping("/register")
  ResponseEntity<TokenResponse> register(
      @Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
    IssuedTokens tokens =
        registration.register(
            new Registration(body.fullName(), body.email(), body.phone(), body.password()),
            client(request));
    return respond(HttpStatus.CREATED, tokens);
  }

  @PostMapping("/login")
  ResponseEntity<TokenResponse> login(
      @Valid @RequestBody AuthRequests.Login body, HttpServletRequest request) {
    return respond(
        HttpStatus.OK, authentication.login(body.identifier(), body.password(), client(request)));
  }

  @PostMapping("/refresh")
  ResponseEntity<TokenResponse> refresh(
      @Valid @RequestBody AuthRequests.Refresh body, HttpServletRequest request) {
    return respond(HttpStatus.OK, refresh.refresh(body.refreshToken(), client(request)));
  }

  /** Token responses must never be cached (RFC 6749 §5.1). */
  private ResponseEntity<TokenResponse> respond(HttpStatus status, IssuedTokens tokens) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(TokenResponse.of(tokens, clock.instant()));
  }

  /**
   * The remote address is the client once Tomcat has applied X-Forwarded-For from trusted internal
   * proxies ({@code server.forward-headers-strategy=native}).
   */
  private static ClientContext client(HttpServletRequest request) {
    return new ClientContext(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
  }
}
