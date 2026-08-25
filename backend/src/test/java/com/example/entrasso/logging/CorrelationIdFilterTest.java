package com.example.entrasso.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Verifies correlation propagation, generation, validation, and MDC cleanup. */
class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  /** Reuses a safe UI-supplied ID in the backend MDC and response header. */
  @Test
  void preservesValidCallerCorrelationId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
    request.addHeader(CorrelationIdFilter.HEADER_NAME, "ui-flow:1234");
    request.addHeader(
        CorrelationIdFilter.TRACEPARENT_HEADER,
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> idSeenByApplication = new AtomicReference<>();
    AtomicReference<String> traceparentSeenByApplication = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (ignoredRequest, ignoredResponse) -> {
          idSeenByApplication.set(MDC.get(CorrelationIdFilter.MDC_KEY));
          traceparentSeenByApplication.set(MDC.get(CorrelationIdFilter.TRACEPARENT_MDC_KEY));
        });

    assertThat(idSeenByApplication).hasValue("ui-flow:1234");
    assertThat(traceparentSeenByApplication)
        .hasValue("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("ui-flow:1234");
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    assertThat(MDC.get(CorrelationIdFilter.TRACEPARENT_MDC_KEY)).isNull();
  }

  /** Creates a UUID when the client does not provide an ID. */
  @Test
  void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

    assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  }

  /** Replaces an unsafe caller value so untrusted control characters cannot enter the MDC. */
  @Test
  void replacesUnsafeCorrelationId() {
    String generated = CorrelationIdFilter.resolveCorrelationId("unsafe value");

    assertThat(generated).isNotEqualTo("unsafe value").hasSize(36);
  }

  /** Rejects malformed and all-zero W3C identifiers instead of placing them in logs. */
  @Test
  void rejectsInvalidTraceparent() {
    assertThat(CorrelationIdFilter.resolveTraceparent("not-a-traceparent")).isEmpty();
    assertThat(
            CorrelationIdFilter.resolveTraceparent(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01"))
        .isEmpty();
    assertThat(CorrelationIdFilter.resolveTraceparent(null)).isEmpty();
  }
}
