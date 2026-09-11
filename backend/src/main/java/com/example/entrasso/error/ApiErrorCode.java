package com.example.entrasso.error;

import java.util.Locale;

/** Defines stable machine codes and safe client-facing text for every API error category. */
public enum ApiErrorCode {
  AUTHENTICATION_REQUIRED("Authentication required", "A valid bearer access token is required."),
  ACCESS_DENIED(
      "Access denied", "The authenticated user does not have permission for this operation."),
  INVALID_REQUEST("Invalid request", "The request is invalid or could not be read."),
  VALIDATION_FAILED("Validation failed", "One or more request values are invalid."),
  RESOURCE_NOT_FOUND("Resource not found", "The requested API resource was not found."),
  METHOD_NOT_ALLOWED("Method not allowed", "The HTTP method is not supported for this resource."),
  NOT_ACCEPTABLE(
      "Response format not acceptable", "The requested response format is not supported."),
  UNSUPPORTED_MEDIA_TYPE("Unsupported media type", "The request content type is not supported."),
  PAYLOAD_TOO_LARGE("Payload too large", "The request payload exceeds the permitted size."),
  REQUEST_TIMEOUT("Request timeout", "The request did not complete within the permitted time."),
  CONFLICT("Conflict", "The request conflicts with the current resource state."),
  DATABASE_CONFLICT(
      "Database conflict", "The operation conflicts with an existing database record."),
  DATABASE_UNAVAILABLE("Database unavailable", "The database operation could not be completed."),
  DOWNSTREAM_SERVICE_ERROR(
      "Downstream service error", "A downstream service returned an unsuccessful response."),
  DOWNSTREAM_SERVICE_UNAVAILABLE(
      "Downstream service unavailable", "A required downstream service is unavailable."),
  MESSAGING_UNAVAILABLE(
      "Messaging system unavailable", "A required messaging operation could not be completed."),
  FILE_TRANSFER_UNAVAILABLE(
      "File transfer unavailable", "A required file-transfer operation could not be completed."),
  INTERNAL_SERVER_ERROR(
      "Internal server error", "An unexpected error occurred while processing the request."),
  HTTP_REQUEST_ERROR("Request rejected", "The request could not be completed."),
  HTTP_SERVER_ERROR("Server error", "The server could not complete the request at this time.");

  private final String title;
  private final String detail;

  /** Creates one error-code definition with text that is safe to return outside the service. */
  ApiErrorCode(String title, String detail) {
    this.title = title;
    this.detail = detail;
  }

  /** Returns the short human-readable category displayed to API clients. */
  public String title() {
    return title;
  }

  /** Returns the default client-safe explanation for this category. */
  public String detail() {
    return detail;
  }

  /** Returns the lowercase URI suffix used by the RFC 9457 {@code type} property. */
  public String typeSlug() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /** Chooses a stable fallback error code for a servlet status without exposing internals. */
  public static ApiErrorCode forHttpStatus(int status) {
    return switch (status) {
      case 400 -> INVALID_REQUEST;
      case 401 -> AUTHENTICATION_REQUIRED;
      case 403 -> ACCESS_DENIED;
      case 404 -> RESOURCE_NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 408 -> REQUEST_TIMEOUT;
      case 409 -> CONFLICT;
      case 413 -> PAYLOAD_TOO_LARGE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      default -> status >= 500 ? HTTP_SERVER_ERROR : HTTP_REQUEST_ERROR;
    };
  }
}
