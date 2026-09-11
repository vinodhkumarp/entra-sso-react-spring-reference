package com.example.entrasso.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Tests the controlled application-error contract and its defensive status validation. */
class ApiExceptionTest {

  /**
   * Verifies the approved status, code, detail, and original cause remain available to handlers.
   */
  @Test
  void retainsControlledErrorMetadata() {
    RuntimeException cause = new RuntimeException("internal cause");
    ApiException exception =
        new ApiException(
            HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, "Safe detail", cause);

    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.errorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
    assertThat(exception.clientDetail()).isEqualTo("Safe detail");
    assertThat(exception.getCause()).isSameAs(cause);
  }

  /** Verifies successful statuses cannot accidentally be used for an exception response. */
  @Test
  void rejectsSuccessfulHttpStatus() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ApiException(
                    HttpStatus.OK, ApiErrorCode.INTERNAL_SERVER_ERROR, "Invalid status"));
  }
}
