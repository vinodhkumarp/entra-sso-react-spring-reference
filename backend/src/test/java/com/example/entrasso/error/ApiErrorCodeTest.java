package com.example.entrasso.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies stable fallback mappings for common and uncommon HTTP error statuses. */
class ApiErrorCodeTest {

  /** Covers every specifically named HTTP status and the generic 4xx/5xx fallbacks. */
  @Test
  void mapsHttpStatusesToStableCodes() {
    assertThat(ApiErrorCode.forHttpStatus(400)).isEqualTo(ApiErrorCode.INVALID_REQUEST);
    assertThat(ApiErrorCode.forHttpStatus(401)).isEqualTo(ApiErrorCode.AUTHENTICATION_REQUIRED);
    assertThat(ApiErrorCode.forHttpStatus(403)).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    assertThat(ApiErrorCode.forHttpStatus(404)).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
    assertThat(ApiErrorCode.forHttpStatus(405)).isEqualTo(ApiErrorCode.METHOD_NOT_ALLOWED);
    assertThat(ApiErrorCode.forHttpStatus(406)).isEqualTo(ApiErrorCode.NOT_ACCEPTABLE);
    assertThat(ApiErrorCode.forHttpStatus(408)).isEqualTo(ApiErrorCode.REQUEST_TIMEOUT);
    assertThat(ApiErrorCode.forHttpStatus(409)).isEqualTo(ApiErrorCode.CONFLICT);
    assertThat(ApiErrorCode.forHttpStatus(413)).isEqualTo(ApiErrorCode.PAYLOAD_TOO_LARGE);
    assertThat(ApiErrorCode.forHttpStatus(415)).isEqualTo(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE);
    assertThat(ApiErrorCode.forHttpStatus(418)).isEqualTo(ApiErrorCode.HTTP_REQUEST_ERROR);
    assertThat(ApiErrorCode.forHttpStatus(500)).isEqualTo(ApiErrorCode.HTTP_SERVER_ERROR);
  }

  /** Verifies public metadata is stable and produces a URI-safe type suffix. */
  @Test
  void exposesSafeErrorMetadata() {
    assertThat(ApiErrorCode.ACCESS_DENIED.title()).isEqualTo("Access denied");
    assertThat(ApiErrorCode.ACCESS_DENIED.detail())
        .isEqualTo("The authenticated user does not have permission for this operation.");
    assertThat(ApiErrorCode.ACCESS_DENIED.typeSlug()).isEqualTo("access-denied");
  }
}
