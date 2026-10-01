package com.fooddelivery.platform.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Method-security failures thrown inside controllers would otherwise reach the catch-all handler
 * and become 500s.
 */
@RestControllerAdvice
@Order(0)
public class SecurityExceptionHandler {

  private final ProblemDetails problems;

  public SecurityExceptionHandler(ProblemDetails problems) {
    this.problems = problems;
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> accessDenied(
      AccessDeniedException ex, HttpServletRequest request, HttpServletResponse response) {
    CommonErrorCode code = CommonErrorCode.FORBIDDEN;
    return problems.response(
        code,
        code.status(),
        "You do not have access to this resource.",
        List.of(),
        request.getRequestURI(),
        response);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> unauthenticated(
      AuthenticationException ex, HttpServletRequest request, HttpServletResponse response) {
    CommonErrorCode code = CommonErrorCode.UNAUTHENTICATED;
    return problems.response(
        code,
        code.status(),
        "A valid access token is required.",
        List.of(),
        request.getRequestURI(),
        response);
  }
}
