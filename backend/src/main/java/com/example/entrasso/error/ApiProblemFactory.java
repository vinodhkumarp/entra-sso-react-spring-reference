package com.example.entrasso.error;

import com.example.entrasso.logging.CorrelationIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectWriter;

/** Creates the application's consistent RFC 9457-style API error response without shared state. */
public final class ApiProblemFactory {

  private static final String TYPE_PREFIX = "urn:problem:entra-sso-reference:";

  /** Prevents instantiation because every factory operation is stateless. */
  private ApiProblemFactory() {}

  /** Creates a problem response without field-level validation details. */
  public static ProblemDetail create(
      HttpStatusCode status, ApiErrorCode errorCode, HttpServletRequest request) {
    return create(status, errorCode, errorCode.detail(), request, List.of());
  }

  /** Creates a problem response with an explicitly approved client-facing detail message. */
  public static ProblemDetail create(
      HttpStatusCode status, ApiErrorCode errorCode, String detail, HttpServletRequest request) {
    return create(status, errorCode, detail, request, List.of());
  }

  /**
   * Creates a complete problem response and attaches optional request-validation violations.
   *
   * <p>Exception messages, rejected values, tokens, credentials, SQL, and downstream response
   * bodies must never be supplied as {@code detail} or violation values.
   */
  public static ProblemDetail create(
      HttpStatusCode status,
      ApiErrorCode errorCode,
      String detail,
      HttpServletRequest request,
      List<ApiValidationViolation> violations) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_PREFIX + errorCode.typeSlug()));
    problem.setTitle(errorCode.title());
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("code", errorCode.name());
    problem.setProperty("correlationId", correlationId(request));
    problem.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC));
    if (!violations.isEmpty()) {
      problem.setProperty("violations", List.copyOf(violations));
    }
    return problem;
  }

  /** Writes a security-filter error with the handler's isolated, immutable JSON writer. */
  public static void write(
      HttpServletResponse response,
      HttpStatusCode status,
      ApiErrorCode errorCode,
      HttpServletRequest request,
      ObjectWriter problemWriter)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    problemWriter.writeValue(response.getOutputStream(), create(status, errorCode, request));
  }

  /** Returns the correlation ID established by the outermost request filter. */
  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE_NAME);
    return value instanceof String correlationId ? correlationId : "";
  }

  /** Returns the original path during an error dispatch and the current path otherwise. */
  private static String requestPath(HttpServletRequest request) {
    Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    return originalPath instanceof String path ? path : request.getRequestURI();
  }
}
