package com.example.entrasso;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.entrasso.error.ApiErrorCode;
import com.example.entrasso.error.ApiException;
import com.example.entrasso.error.ExternalDependencyException;
import com.example.entrasso.error.ExternalDependencyException.DependencyType;
import com.example.entrasso.logging.CorrelationIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Exercises the global error contract across HTTP, security, and infrastructure boundaries. */
@SpringBootTest(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
      "spring.security.oauth2.resourceserver.jwt.audiences=test-api"
    })
@AutoConfigureMockMvc
@Import(ApiErrorHandlingTest.ErrorTestConfiguration.class)
class ApiErrorHandlingTest {

  private static final String CORRELATION_ID = "error-test-123";

  @Autowired private MockMvc mockMvc;

  /**
   * Verifies unmapped routes, unsupported methods, and missing parameters use custom 4xx errors.
   */
  @Test
  void handlesSpringMvcClientErrors() throws Exception {
    expectProblem(
        mockMvc.perform(
            get("/api/not-present").with(scopedToken()).header("X-Correlation-Id", CORRELATION_ID)),
        404,
        "RESOURCE_NOT_FOUND",
        "The requested API resource was not found.",
        "/api/not-present");

    expectProblem(
            mockMvc.perform(
                post("/api/dashboard")
                    .with(scopedToken())
                    .header("X-Correlation-Id", CORRELATION_ID)),
            405,
            "METHOD_NOT_ALLOWED",
            "The HTTP method is not supported for this resource.",
            "/api/dashboard")
        .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));

    expectProblem(
        mockMvc.perform(
            get("/api/test/errors/required-parameter")
                .with(scopedToken())
                .header("X-Correlation-Id", CORRELATION_ID)),
        400,
        "INVALID_REQUEST",
        "The request is invalid or could not be read.",
        "/api/test/errors/required-parameter");
  }

  /** Verifies the final servlet fallback retains arbitrary 4xx/5xx statuses and original paths. */
  @Test
  void handlesServletErrorDispatches() throws Exception {
    expectProblem(
        mockMvc.perform(
            get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 418)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/original-path")
                .requestAttr(CorrelationIdFilter.REQUEST_ATTRIBUTE_NAME, CORRELATION_ID)
                .header("X-Correlation-Id", CORRELATION_ID)),
        418,
        "HTTP_REQUEST_ERROR",
        "The request could not be completed.",
        "/api/original-path");

    expectProblem(
        mockMvc.perform(get("/error").header("X-Correlation-Id", CORRELATION_ID)),
        500,
        "HTTP_SERVER_ERROR",
        "The server could not complete the request at this time.",
        "/error");
  }

  /** Verifies explicit framework HTTP statuses keep their status but never expose their reason. */
  @Test
  void handlesExplicitHttpStatusExceptions() throws Exception {
    expectProblem(
            call("http-status"),
            422,
            "HTTP_REQUEST_ERROR",
            "The request could not be completed.",
            "/api/test/errors/http-status")
        .andExpect(content().string(not(containsString("secret status reason"))));
  }

  /** Verifies intentional business errors preserve only an explicitly approved client message. */
  @Test
  void handlesControlledApplicationError() throws Exception {
    expectProblem(
        call("application"),
        409,
        "CONFLICT",
        "The requested report is already queued.",
        "/api/test/errors/application");
  }

  /** Verifies translated database failures do not expose SQL or connection information. */
  @Test
  void handlesDatabaseErrors() throws Exception {
    expectProblem(
        call("database-conflict"),
        409,
        "DATABASE_CONFLICT",
        "The operation conflicts with an existing database record.",
        "/api/test/errors/database-conflict");
    expectProblem(
        call("database-unavailable"),
        503,
        "DATABASE_UNAVAILABLE",
        "The database operation could not be completed.",
        "/api/test/errors/database-unavailable");
  }

  /** Verifies downstream response, connectivity, and generic client failures have safe mappings. */
  @Test
  void handlesDownstreamHttpErrors() throws Exception {
    expectProblem(
        call("downstream-response"),
        502,
        "DOWNSTREAM_SERVICE_ERROR",
        "A downstream service returned an unsuccessful response.",
        "/api/test/errors/downstream-response");
    expectProblem(
        call("downstream-unavailable"),
        503,
        "DOWNSTREAM_SERVICE_UNAVAILABLE",
        "A required downstream service is unavailable.",
        "/api/test/errors/downstream-unavailable");
    expectProblem(
        call("downstream-client"),
        502,
        "DOWNSTREAM_SERVICE_ERROR",
        "A downstream service returned an unsuccessful response.",
        "/api/test/errors/downstream-client");
  }

  /** Verifies future non-HTTP adapters can wrap failures without leaking vendor exceptions. */
  @Test
  void handlesWrappedInfrastructureErrors() throws Exception {
    expectProblem(
        call("messaging"),
        503,
        "MESSAGING_UNAVAILABLE",
        "A required messaging operation could not be completed.",
        "/api/test/errors/messaging");
    expectProblem(
        call("file-transfer"),
        503,
        "FILE_TRANSFER_UNAVAILABLE",
        "A required file-transfer operation could not be completed.",
        "/api/test/errors/file-transfer");
    expectProblem(
        call("wrapped-database"),
        503,
        "DATABASE_UNAVAILABLE",
        "The database operation could not be completed.",
        "/api/test/errors/wrapped-database");
    expectProblem(
        call("wrapped-api"),
        502,
        "DOWNSTREAM_SERVICE_ERROR",
        "A downstream service returned an unsuccessful response.",
        "/api/test/errors/wrapped-api");
  }

  /** Verifies validation responses contain safe paths and omit rejected values. */
  @Test
  void handlesValidationErrors() throws Exception {
    expectProblem(
            call("validation"),
            400,
            "VALIDATION_FAILED",
            "One or more request values are invalid.",
            "/api/test/errors/validation")
        .andExpect(jsonPath("$.violations[0].field").value("name"))
        .andExpect(jsonPath("$.violations[0].message").value("must not be blank"))
        .andExpect(content().string(not(containsString("rejectedValue"))));
  }

  /**
   * Verifies application-level authentication and unexpected failures remain safe and traceable.
   */
  @Test
  void handlesSecurityAndUnexpectedApplicationErrors() throws Exception {
    expectProblem(
            call("authentication"),
            401,
            "AUTHENTICATION_REQUIRED",
            "A valid bearer access token is required.",
            "/api/test/errors/authentication")
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    expectProblem(
            call("unexpected"),
            500,
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred while processing the request.",
            "/api/test/errors/unexpected")
        .andExpect(content().string(not(containsString("secret implementation detail"))));
  }

  /** Performs an authenticated request to a test-only failure endpoint. */
  private ResultActions call(String errorName) throws Exception {
    return mockMvc.perform(
        get("/api/test/errors/{errorName}", errorName)
            .with(scopedToken())
            .header("X-Correlation-Id", CORRELATION_ID));
  }

  /** Verifies the common problem fields and absence of Spring's internal diagnostic fields. */
  private static ResultActions expectProblem(
      ResultActions result,
      int expectedStatus,
      String expectedCode,
      String expectedDetail,
      String expectedPath)
      throws Exception {
    return result
        .andExpect(status().is(expectedStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(expectedStatus))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andExpect(jsonPath("$.detail").value(expectedDetail))
        .andExpect(jsonPath("$.instance").value(expectedPath))
        .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.exception").doesNotExist())
        .andExpect(jsonPath("$.stackTrace").doesNotExist());
  }

  /** Creates a valid delegated-scope authentication for the test-only endpoints. */
  private static RequestPostProcessor scopedToken() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"));
  }

  /** Registers a local decoder and test-only endpoints without changing production API code. */
  @TestConfiguration(proxyBeanMethods = false)
  static class ErrorTestConfiguration {

    /** Creates a decoder that cannot contact Microsoft during tests. */
    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        throw new BadJwtException("test decoder rejected token");
      };
    }

    /** Creates the failure simulator with the application's configured Jakarta validator. */
    @Bean
    ErrorTestController errorTestController(Validator validator) {
      return new ErrorTestController(validator);
    }
  }

  /** Provides isolated failure simulations used only by the global-handler integration tests. */
  @RestController
  static final class ErrorTestController {

    private final Validator validator;

    /** Creates the test controller with the configured validator. */
    ErrorTestController(Validator validator) {
      this.validator = validator;
    }

    /** Simulates a required request parameter for Spring MVC's own HTTP 400 handling. */
    @GetMapping("/api/test/errors/required-parameter")
    String requiredParameter(@RequestParam String value) {
      return value;
    }

    /** Simulates each future or current exception source by a stable test-only route name. */
    @GetMapping("/api/test/errors/{errorName}")
    void fail(@PathVariable String errorName) {
      switch (errorName) {
        case "application" ->
            throw new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.CONFLICT,
                "The requested report is already queued.");
        case "database-conflict" ->
            throw new DuplicateKeyException("secret_table unique constraint");
        case "database-unavailable" ->
            throw new DataAccessResourceFailureException("jdbc:secret-host unavailable");
        case "downstream-response" ->
            throw HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "downstream-internal-message",
                HttpHeaders.EMPTY,
                "secret downstream body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        case "downstream-unavailable" ->
            throw new ResourceAccessException("secret downstream host timed out");
        case "downstream-client" ->
            throw new RestClientException("secret client configuration failure");
        case "messaging" ->
            throw dependency(DependencyType.MESSAGE_BROKER, "secret Kafka broker failure");
        case "file-transfer" ->
            throw dependency(DependencyType.FILE_TRANSFER, "secret SFTP host failure");
        case "wrapped-database" ->
            throw dependency(DependencyType.DATABASE, "secret database driver failure");
        case "wrapped-api" ->
            throw dependency(DependencyType.DOWNSTREAM_API, "secret downstream API failure");
        case "validation" -> throw validationFailure();
        case "authentication" ->
            throw new AuthenticationCredentialsNotFoundException(
                "secret authentication implementation detail");
        case "http-status" ->
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "secret status reason");
        default -> throw new IllegalStateException("secret implementation detail");
      }
    }

    /** Builds a representative wrapper for an unavailable external dependency. */
    private static ExternalDependencyException dependency(
        DependencyType type, String internalMessage) {
      return new ExternalDependencyException(type, internalMessage, new RuntimeException("cause"));
    }

    /** Builds real Jakarta constraint violations without adding validation code to production. */
    private ConstraintViolationException validationFailure() {
      return new ConstraintViolationException(validator.validate(new ValidationCommand("")));
    }
  }

  /** Test-only input used to create a safe validation error. */
  private record ValidationCommand(@Valid @NotBlank String name) {}
}
