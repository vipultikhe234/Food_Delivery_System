package com.fooddelivery.platform.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Maps every exception that leaves a controller to the platform error format. Stack traces, SQL and
 * class names are logged with the correlation id and never sent to the client (REQ-PLAT-004 AC2).
 *
 * <p>Runs last, so the narrower handlers registered for persistence and security exceptions win.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemDetails problems;

  public GlobalExceptionHandler(ProblemDetails problems) {
    this.problems = problems;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(
      ApiException ex, HttpServletRequest request, HttpServletResponse response) {
    HttpStatusCode status = ex.errorCode().status();
    if (status.is5xxServerError()) {
      log.error("Request failed with {}", ex.errorCode().code(), ex);
    } else {
      log.debug("Request rejected: {}", ex.getMessage());
    }
    ResponseEntity<ProblemDetail> entity =
        problems.response(
            ex.errorCode(), status, ex.detail(), ex.errors(), request.getRequestURI(), response);
    return ResponseEntity.status(entity.getStatusCode())
        .headers(h -> ex.headers().forEach(h::set))
        .headers(entity.getHeaders())
        .body(entity.getBody());
  }

  @ExceptionHandler(BindException.class)
  ResponseEntity<ProblemDetail> bind(
      BindException ex, HttpServletRequest request, HttpServletResponse response) {
    List<FieldViolation> errors = new ArrayList<>();
    for (FieldError error : ex.getFieldErrors()) {
      errors.add(new FieldViolation(error.getField(), rule(error.getCode()), message(error)));
    }
    ex.getGlobalErrors()
        .forEach(
            e ->
                errors.add(
                    new FieldViolation(
                        e.getObjectName(), rule(e.getCode()), e.getDefaultMessage())));
    return validation("The request has invalid fields.", errors, request, response);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ProblemDetail> methodValidation(
      HandlerMethodValidationException ex,
      HttpServletRequest request,
      HttpServletResponse response) {
    List<FieldViolation> errors = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(
            result -> {
              String name = result.getMethodParameter().getParameterName();
              result
                  .getResolvableErrors()
                  .forEach(
                      error ->
                          errors.add(
                              new FieldViolation(
                                  name,
                                  rule(error.getCodes() == null ? null : error.getCodes()[0]),
                                  error.getDefaultMessage())));
            });
    return validation("The request has invalid parameters.", errors, request, response);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> constraintViolation(
      ConstraintViolationException ex, HttpServletRequest request, HttpServletResponse response) {
    List<FieldViolation> errors =
        ex.getConstraintViolations().stream()
            .map(
                v ->
                    new FieldViolation(
                        v.getPropertyPath().toString(),
                        rule(
                            v.getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName()),
                        v.getMessage()))
            .toList();
    return validation("The request has invalid values.", errors, request, response);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> unreadable(
      HttpMessageNotReadableException ex,
      HttpServletRequest request,
      HttpServletResponse response) {
    log.debug("Unreadable request body: {}", ex.getMessage());
    List<FieldViolation> errors = new ArrayList<>();
    if (ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
      String code =
          jackson instanceof UnrecognizedPropertyException ? "UNKNOWN_PROPERTY" : "INVALID_VALUE";
      String message =
          jackson instanceof UnrecognizedPropertyException
              ? "is not a known field"
              : "has an invalid value or type";
      errors.add(new FieldViolation(path(jackson.getPath()), code, message));
    }
    return validation("The request body is malformed.", errors, request, response);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ProblemDetail> missingHeader(
      MissingRequestHeaderException ex, HttpServletRequest request, HttpServletResponse response) {
    return validation(
        "A required header is missing.",
        List.of(new FieldViolation(ex.getHeaderName(), "REQUIRED", "header is required")),
        request,
        response);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ProblemDetail> missingParameter(
      MissingServletRequestParameterException ex,
      HttpServletRequest request,
      HttpServletResponse response) {
    return validation(
        "A required parameter is missing.",
        List.of(new FieldViolation(ex.getParameterName(), "REQUIRED", "parameter is required")),
        request,
        response);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ProblemDetail> typeMismatch(
      MethodArgumentTypeMismatchException ex,
      HttpServletRequest request,
      HttpServletResponse response) {
    return validation(
        "A parameter has an invalid value.",
        List.of(new FieldViolation(ex.getName(), "INVALID_VALUE", "has an invalid value or type")),
        request,
        response);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ProblemDetail> responseStatus(
      ResponseStatusException ex, HttpServletRequest request, HttpServletResponse response) {
    HttpStatusCode status = ex.getStatusCode();
    String detail = ex.getReason() != null ? ex.getReason() : StatusDetails.of(status);
    logByStatus(status, ex);
    return problems.response(
        CommonErrorCode.forStatus(status.value()),
        status,
        detail,
        List.of(),
        request.getRequestURI(),
        response);
  }

  /**
   * Everything else. Spring MVC's own exceptions (404, 405, 406, 415, ...) implement {@link
   * ErrorResponse}: their status is kept and their text replaced with a fixed one.
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(
      Exception ex, HttpServletRequest request, HttpServletResponse response) {
    if (ex instanceof ErrorResponse framework) {
      HttpStatusCode status = framework.getStatusCode();
      logByStatus(status, ex);
      return problems.response(
          CommonErrorCode.forStatus(status.value()),
          status,
          StatusDetails.of(status),
          List.of(),
          request.getRequestURI(),
          response);
    }
    log.error("Unhandled exception", ex);
    CommonErrorCode code = CommonErrorCode.INTERNAL_ERROR;
    return problems.response(
        code, code.status(), null, List.of(), request.getRequestURI(), response);
  }

  private ResponseEntity<ProblemDetail> validation(
      String detail,
      List<FieldViolation> errors,
      HttpServletRequest request,
      HttpServletResponse response) {
    CommonErrorCode code = CommonErrorCode.VALIDATION_FAILED;
    return problems.response(
        code, code.status(), detail, errors, request.getRequestURI(), response);
  }

  private static void logByStatus(HttpStatusCode status, Exception ex) {
    if (status.is5xxServerError()) {
      log.error("Request failed with status {}", status.value(), ex);
    } else {
      log.debug("Request rejected with status {}: {}", status.value(), ex.toString());
    }
  }

  private static String message(FieldError error) {
    return error.isBindingFailure() ? "has an invalid value or type" : error.getDefaultMessage();
  }

  /** {@code NotBlank} becomes {@code NOT_BLANK}; binding failures become {@code INVALID_VALUE}. */
  static String rule(String code) {
    if (code == null || code.isBlank() || code.equals("typeMismatch")) {
      return "INVALID_VALUE";
    }
    return code.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }

  private static String path(List<JacksonException.Reference> references) {
    StringBuilder path = new StringBuilder();
    for (JacksonException.Reference reference : references) {
      if (reference.getPropertyName() != null) {
        if (!path.isEmpty()) {
          path.append('.');
        }
        path.append(reference.getPropertyName());
      } else if (reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex()).append(']');
      }
    }
    return path.toString();
  }
}
