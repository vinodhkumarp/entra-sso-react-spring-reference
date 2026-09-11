package com.example.entrasso.error;

import java.io.Serial;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;

/** Represents an intentional API failure with an approved status, code, and client-safe message. */
public final class ApiException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final HttpStatusCode status;
  private final ApiErrorCode errorCode;
  private final String clientDetail;

  /**
   * Creates a controlled API failure.
   *
   * <p>For example, a service may throw this with status {@code 404}, code {@code
   * RESOURCE_NOT_FOUND}, and a message such as {@code "The requested report was not found."}. Never
   * include secrets or internal implementation details in {@code clientDetail}.
   */
  public ApiException(
      HttpStatusCode status, ApiErrorCode errorCode, String clientDetail, Throwable cause) {
    super(clientDetail, cause);
    this.status = requireErrorStatus(status);
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode is required");
    this.clientDetail = Objects.requireNonNull(clientDetail, "clientDetail is required");
  }

  /** Creates a controlled API failure that does not wrap another exception. */
  public ApiException(HttpStatusCode status, ApiErrorCode errorCode, String clientDetail) {
    this(status, errorCode, clientDetail, null);
  }

  /** Returns the HTTP status that should be sent to the client. */
  public HttpStatusCode status() {
    return status;
  }

  /** Returns the stable machine-readable error code. */
  public ApiErrorCode errorCode() {
    return errorCode;
  }

  /** Returns the explicitly approved detail that may be sent to the client. */
  public String clientDetail() {
    return clientDetail;
  }

  /** Rejects accidental use of a successful HTTP status for an exception. */
  private static HttpStatusCode requireErrorStatus(HttpStatusCode status) {
    Objects.requireNonNull(status, "status is required");
    if (!status.isError()) {
      throw new IllegalArgumentException("ApiException status must be a 4xx or 5xx value");
    }
    return status;
  }
}
