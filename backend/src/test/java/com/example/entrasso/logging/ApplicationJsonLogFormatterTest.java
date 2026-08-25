package com.example.entrasso.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Verifies the exact JSON logging contract, escaping, MDC fields, and exception rendering. */
class ApplicationJsonLogFormatterTest {

  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
  private final ApplicationJsonLogFormatter formatter = new ApplicationJsonLogFormatter();

  /** Produces exactly the six required fields and preserves formatted message characters. */
  @Test
  void formatsRequestedJsonProperties() throws Exception {
    LoggingEvent event =
        event(
            Level.INFO,
            "Completed \"quoted\" request",
            null,
            Map.of(
                CorrelationIdFilter.MDC_KEY,
                "ui-request-123",
                CorrelationIdFilter.TRACEPARENT_MDC_KEY,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));

    Map<String, String> json = JsonMapper.shared().readValue(formatter.format(event), STRING_MAP);

    assertThat(json)
        .containsOnlyKeys(
            "X-Correlation-Id", "level", "message", "logger", "traceparent", "stack_trace")
        .containsEntry("X-Correlation-Id", "ui-request-123")
        .containsEntry("level", "INFO")
        .containsEntry("message", "Completed \"quoted\" request")
        .containsEntry("logger", "com.example.TestLogger")
        .containsEntry("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
        .containsEntry("stack_trace", "");
  }

  /** Serializes a complete stack trace into the stack_trace property when an exception exists. */
  @Test
  void formatsExceptionStackTrace() throws Exception {
    IllegalStateException failure = new IllegalStateException("example failure");
    LoggingEvent event = event(Level.ERROR, "Request failed", failure, Map.of());

    Map<String, String> json = JsonMapper.shared().readValue(formatter.format(event), STRING_MAP);

    assertThat(json.get("stack_trace"))
        .contains("java.lang.IllegalStateException: example failure")
        .contains("ApplicationJsonLogFormatterTest");
    assertThat(json.get("X-Correlation-Id")).isEmpty();
    assertThat(json.get("traceparent")).isEmpty();
  }

  /** Creates a representative Logback event without starting the Spring application context. */
  private static LoggingEvent event(
      Level level, String message, Throwable throwable, Map<String, String> mdc) {
    LoggingEvent event = new LoggingEvent();
    event.setLevel(level);
    event.setLoggerName("com.example.TestLogger");
    event.setMessage(message);
    event.setMDCPropertyMap(mdc);
    if (throwable != null) {
      event.setThrowableProxy(new ThrowableProxy(throwable));
    }
    return event;
  }
}
