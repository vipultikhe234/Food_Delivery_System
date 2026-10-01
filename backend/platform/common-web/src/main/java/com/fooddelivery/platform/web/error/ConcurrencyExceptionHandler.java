package com.fooddelivery.platform.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A stale {@code version} on update returns 409 (REQ-PLAT-008 AC4). */
@RestControllerAdvice
@Order(0)
public class ConcurrencyExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ConcurrencyExceptionHandler.class);

  private final ProblemDetails problems;

  public ConcurrencyExceptionHandler(ProblemDetails problems) {
    this.problems = problems;
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> optimisticLock(
      OptimisticLockingFailureException ex,
      HttpServletRequest request,
      HttpServletResponse response) {
    log.debug("Optimistic lock conflict: {}", ex.getMessage());
    CommonErrorCode code = CommonErrorCode.CONCURRENT_MODIFICATION;
    return problems.response(
        code,
        code.status(),
        "The resource was changed by another request. Reload it and try again.",
        List.of(),
        request.getRequestURI(),
        response);
  }
}
