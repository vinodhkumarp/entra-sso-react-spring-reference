package com.example.entrasso.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds one safe correlation ID to every inbound request, response, and request log entry. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {

  /** HTTP header shared with the React client and any upstream gateway. */
  public static final String HEADER_NAME = "X-Correlation-Id";

  /** MDC key rendered by the configured Spring Boot logging pattern. */
  public static final String MDC_KEY = "correlationId";

  /** Request attribute used by centralized error handling to populate the response body. */
  public static final String REQUEST_ATTRIBUTE_NAME =
      CorrelationIdFilter.class.getName() + ".correlationId";

  /** W3C Trace Context header accepted from an instrumented browser or upstream gateway. */
  public static final String TRACEPARENT_HEADER = "traceparent";

  /** MDC key rendered as the JSON {@code traceparent} property. */
  public static final String TRACEPARENT_MDC_KEY = "traceparent";

  private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);
  private static final int MAX_ID_LENGTH = 128;
  private static final Pattern SAFE_ID =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0," + (MAX_ID_LENGTH - 1) + "}");
  private static final Pattern TRACEPARENT =
      Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");
  private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
  private static final String ZERO_PARENT_ID = "0000000000000000";

  /** Creates the filter; Spring Boot registers this component before Spring Security. */
  public CorrelationIdFilter() {}

  /**
   * Reuses a valid caller ID or creates a UUID, places it in MDC, and logs request completion.
   *
   * <p>The filter records only the method, path, status, and duration. It intentionally excludes
   * authorization headers, query strings, request bodies, and identity claims.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
    String traceparent = resolveTraceparent(request.getHeader(TRACEPARENT_HEADER));
    request.setAttribute(REQUEST_ATTRIBUTE_NAME, correlationId);
    response.setHeader(HEADER_NAME, correlationId);
    long startedAt = System.nanoTime();

    try (MDC.MDCCloseable ignoredCorrelationId = MDC.putCloseable(MDC_KEY, correlationId);
        MDC.MDCCloseable ignoredTraceparent = MDC.putCloseable(TRACEPARENT_MDC_KEY, traceparent)) {
      try {
        filterChain.doFilter(request, response);
        logCompletion(request, response.getStatus(), elapsedMilliseconds(startedAt));
      } catch (IOException | ServletException | RuntimeException exception) {
        LOGGER.error(
            "HTTP {} {} failed after {} ms",
            request.getMethod(),
            request.getRequestURI(),
            elapsedMilliseconds(startedAt),
            exception);
        throw exception;
      }
    }
  }

  /** Returns the caller value when it is safe for logs, otherwise creates a new UUID. */
  static String resolveCorrelationId(String candidate) {
    if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  /** Returns a valid W3C version-00 traceparent or an empty value when it is absent or unsafe. */
  static String resolveTraceparent(String candidate) {
    if (candidate == null) {
      return "";
    }

    var matcher = TRACEPARENT.matcher(candidate);
    if (!matcher.matches()
        || ZERO_TRACE_ID.equals(matcher.group(1))
        || ZERO_PARENT_ID.equals(matcher.group(2))) {
      return "";
    }
    return candidate;
  }

  /** Logs successful responses at INFO, client errors at WARN, and server errors at ERROR. */
  private static void logCompletion(
      HttpServletRequest request, int status, long durationMilliseconds) {
    if (status >= 500) {
      LOGGER.error(
          "HTTP {} {} completed with status={} in {} ms",
          request.getMethod(),
          request.getRequestURI(),
          status,
          durationMilliseconds);
    } else if (status >= 400) {
      LOGGER.warn(
          "HTTP {} {} completed with status={} in {} ms",
          request.getMethod(),
          request.getRequestURI(),
          status,
          durationMilliseconds);
    } else {
      LOGGER.info(
          "HTTP {} {} completed with status={} in {} ms",
          request.getMethod(),
          request.getRequestURI(),
          status,
          durationMilliseconds);
    }
  }

  /** Converts monotonic elapsed nanoseconds to whole milliseconds for a stable log field. */
  private static long elapsedMilliseconds(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }
}
