package com.fooddelivery.platform.web.error;

import org.springframework.http.HttpStatusCode;

/** Fixed client-facing texts for errors that only carry an HTTP status. */
final class StatusDetails {

  private StatusDetails() {}

  static String of(HttpStatusCode status) {
    return switch (status.value()) {
      case 401 -> "A valid access token is required.";
      case 403 -> "You do not have access to this resource.";
      case 404 -> "The requested resource was not found.";
      case 405 -> "The HTTP method is not supported for this resource.";
      case 406, 415 -> "The requested media type is not supported.";
      case 413 -> "The request is too large.";
      case 429 -> "Too many requests. Try again later.";
      case 503 -> "The service is temporarily unavailable.";
      default ->
          status.is5xxServerError()
              ? ProblemDetails.GENERIC_SERVER_DETAIL
              : "The request could not be processed.";
    };
  }
}
