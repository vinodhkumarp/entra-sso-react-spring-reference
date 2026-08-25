package com.example.entrasso.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.util.Map;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/** Formats every backend log event as one JSON line with the application's approved fields. */
public final class ApplicationJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

  private static final JsonWriter<ILoggingEvent> JSON_WRITER =
      JsonWriter.<ILoggingEvent>of(
              members -> {
                members.add(
                    "X-Correlation-Id", event -> mdcValue(event, CorrelationIdFilter.MDC_KEY));
                members.add("level", event -> event.getLevel().toString());
                members.add("message", ILoggingEvent::getFormattedMessage);
                members.add("logger", ILoggingEvent::getLoggerName);
                members.add(
                    "traceparent",
                    event -> mdcValue(event, CorrelationIdFilter.TRACEPARENT_MDC_KEY));
                members.add("stack_trace", ApplicationJsonLogFormatter::stackTrace);
              })
          .withNewLineAtEnd();

  /** Creates the stateless formatter used by Spring Boot's structured Logback encoder. */
  public ApplicationJsonLogFormatter() {}

  /** Converts one Logback event to a correctly escaped JSON document followed by a newline. */
  @Override
  public String format(ILoggingEvent event) {
    return JSON_WRITER.writeToString(event);
  }

  /** Returns a named MDC value or an empty string so the JSON schema remains stable. */
  private static String mdcValue(ILoggingEvent event, String key) {
    Map<String, String> mdc = event.getMDCPropertyMap();
    return mdc == null ? "" : mdc.getOrDefault(key, "");
  }

  /** Returns the complete rendered exception or an empty string for ordinary log events. */
  private static String stackTrace(ILoggingEvent event) {
    IThrowableProxy throwable = event.getThrowableProxy();
    return throwable == null ? "" : ThrowableProxyUtil.asString(throwable);
  }
}
