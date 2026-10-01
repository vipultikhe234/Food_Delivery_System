package com.fooddelivery.platform.web.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's error controller so that errors raised outside controllers (filters,
 * container) use the same format as the rest of the API.
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ProblemErrorController implements ErrorController {

  private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);

  private final ProblemDetails problems;

  public ProblemErrorController(ProblemDetails problems) {
    this.problems = problems;
  }

  @RequestMapping
  ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse response) {
    String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    Throwable failure = unwrap((Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));

    if (failure instanceof ApiException api) {
      return problems.response(
          api.errorCode(), api.errorCode().status(), api.detail(), api.errors(), path, response);
    }

    HttpStatusCode status = status(request);
    if (status.is5xxServerError()) {
      log.error("Request to {} failed with status {}", path, status.value(), failure);
    }
    return problems.response(
        CommonErrorCode.forStatus(status.value()),
        status,
        StatusDetails.of(status),
        List.of(),
        path,
        response);
  }

  private static HttpStatusCode status(HttpServletRequest request) {
    Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    return code instanceof Integer value && value >= 400
        ? HttpStatusCode.valueOf(value)
        : HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private static Throwable unwrap(Throwable failure) {
    Throwable current = failure;
    while (current instanceof ServletException servlet && servlet.getRootCause() != null) {
      current = servlet.getRootCause();
    }
    return current;
  }
}
