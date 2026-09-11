package com.example.entrasso.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converts MVC, application, database, and downstream failures to one safe JSON contract. */
@RestControllerAdvice
public final class GlobalApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

  /** Returns an intentional application error using only its explicitly approved client detail. */
  @ExceptionHandler(ApiException.class)
  ResponseEntity<Object> handleApiException(ApiException exception, HttpServletRequest request) {
    if (exception.status().is5xxServerError()) {
      LOGGER.error("Application API operation failed", exception);
    }
    return response(
        exception.status(), exception.errorCode(), exception.clientDetail(), request, List.of());
  }

  /** Converts method or service validation failures to HTTP 400 with safe field paths. */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    List<ApiValidationViolation> violations =
        exception.getConstraintViolations().stream()
            .map(
                violation ->
                    new ApiValidationViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
            .toList();
    return response(
        HttpStatus.BAD_REQUEST,
        ApiErrorCode.VALIDATION_FAILED,
        ApiErrorCode.VALIDATION_FAILED.detail(),
        request,
        violations);
  }

  /** Converts an application-level authentication failure to the custom HTTP 401 contract. */
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Object> handleAuthenticationFailure(
      AuthenticationException exception, HttpServletRequest request) {
    HttpHeaders headers = problemHeaders();
    headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    return response(
        HttpStatus.UNAUTHORIZED,
        ApiErrorCode.AUTHENTICATION_REQUIRED,
        ApiErrorCode.AUTHENTICATION_REQUIRED.detail(),
        request,
        List.of(),
        headers);
  }

  /** Converts method-level authorization rejection to the custom HTTP 403 contract. */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> handleAccessDenied(
      AccessDeniedException exception, HttpServletRequest request) {
    return response(
        HttpStatus.FORBIDDEN,
        ApiErrorCode.ACCESS_DENIED,
        ApiErrorCode.ACCESS_DENIED.detail(),
        request,
        List.of());
  }

  /** Treats uniqueness and referential-integrity failures as a safe HTTP 409 response. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Object> handleDatabaseConflict(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    LOGGER.warn("Database integrity operation failed", exception);
    return response(
        HttpStatus.CONFLICT,
        ApiErrorCode.DATABASE_CONFLICT,
        ApiErrorCode.DATABASE_CONFLICT.detail(),
        request,
        List.of());
  }

  /** Hides database vendor, SQL, host, and schema details behind a retryable HTTP 503 response. */
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Object> handleDatabaseFailure(
      DataAccessException exception, HttpServletRequest request) {
    LOGGER.error("Database operation failed", exception);
    return response(
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiErrorCode.DATABASE_UNAVAILABLE,
        ApiErrorCode.DATABASE_UNAVAILABLE.detail(),
        request,
        List.of());
  }

  /** Converts an unsuccessful downstream HTTP response to HTTP 502 without proxying its body. */
  @ExceptionHandler(RestClientResponseException.class)
  ResponseEntity<Object> handleDownstreamResponseFailure(
      RestClientResponseException exception, HttpServletRequest request) {
    LOGGER.error(
        "Downstream HTTP operation returned status={}", exception.getStatusCode(), exception);
    return response(
        HttpStatus.BAD_GATEWAY,
        ApiErrorCode.DOWNSTREAM_SERVICE_ERROR,
        ApiErrorCode.DOWNSTREAM_SERVICE_ERROR.detail(),
        request,
        List.of());
  }

  /** Converts connection, DNS, and client timeout failures to a retryable HTTP 503 response. */
  @ExceptionHandler(ResourceAccessException.class)
  ResponseEntity<Object> handleDownstreamUnavailable(
      ResourceAccessException exception, HttpServletRequest request) {
    LOGGER.error("Downstream HTTP service is unavailable", exception);
    return response(
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiErrorCode.DOWNSTREAM_SERVICE_UNAVAILABLE,
        ApiErrorCode.DOWNSTREAM_SERVICE_UNAVAILABLE.detail(),
        request,
        List.of());
  }

  /** Converts any other Spring HTTP client failure to a safe HTTP 502 response. */
  @ExceptionHandler(RestClientException.class)
  ResponseEntity<Object> handleDownstreamClientFailure(
      RestClientException exception, HttpServletRequest request) {
    LOGGER.error("Downstream HTTP client operation failed", exception);
    return response(
        HttpStatus.BAD_GATEWAY,
        ApiErrorCode.DOWNSTREAM_SERVICE_ERROR,
        ApiErrorCode.DOWNSTREAM_SERVICE_ERROR.detail(),
        request,
        List.of());
  }

  /** Converts wrapped future Kafka, SFTP, API, or non-Spring database client failures. */
  @ExceptionHandler(ExternalDependencyException.class)
  ResponseEntity<Object> handleExternalDependencyFailure(
      ExternalDependencyException exception, HttpServletRequest request) {
    LOGGER.error("External dependency operation failed: {}", exception.dependencyType(), exception);
    return switch (exception.dependencyType()) {
      case DATABASE ->
          response(
              HttpStatus.SERVICE_UNAVAILABLE,
              ApiErrorCode.DATABASE_UNAVAILABLE,
              ApiErrorCode.DATABASE_UNAVAILABLE.detail(),
              request,
              List.of());
      case DOWNSTREAM_API ->
          response(
              HttpStatus.BAD_GATEWAY,
              ApiErrorCode.DOWNSTREAM_SERVICE_ERROR,
              ApiErrorCode.DOWNSTREAM_SERVICE_ERROR.detail(),
              request,
              List.of());
      case MESSAGE_BROKER ->
          response(
              HttpStatus.SERVICE_UNAVAILABLE,
              ApiErrorCode.MESSAGING_UNAVAILABLE,
              ApiErrorCode.MESSAGING_UNAVAILABLE.detail(),
              request,
              List.of());
      case FILE_TRANSFER ->
          response(
              HttpStatus.SERVICE_UNAVAILABLE,
              ApiErrorCode.FILE_TRANSFER_UNAVAILABLE,
              ApiErrorCode.FILE_TRANSFER_UNAVAILABLE.detail(),
              request,
              List.of());
    };
  }

  /** Prevents uncaught implementation details from reaching the client for unexpected failures. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpectedFailure(Exception exception, HttpServletRequest request) {
    LOGGER.error("Unhandled API operation failed", exception);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiErrorCode.INTERNAL_SERVER_ERROR,
        ApiErrorCode.INTERNAL_SERVER_ERROR.detail(),
        request,
        List.of());
  }

  /** Replaces the body for every built-in Spring MVC HTTP exception. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest webRequest) {
    if (status.is5xxServerError()) {
      LOGGER.error(
          "Spring MVC request processing failed with status={}", status.value(), exception);
    }

    ApiErrorCode errorCode = mvcErrorCode(exception, status);
    List<ApiValidationViolation> violations = validationViolations(exception);
    return response(
        status, errorCode, errorCode.detail(), servletRequest(webRequest), violations, headers);
  }

  /** Maps built-in MVC exceptions to stable codes instead of returning framework messages. */
  private static ApiErrorCode mvcErrorCode(Exception exception, HttpStatusCode status) {
    if (exception instanceof MethodArgumentNotValidException
        || exception instanceof HandlerMethodValidationException) {
      return ApiErrorCode.VALIDATION_FAILED;
    }
    if (exception instanceof NoResourceFoundException
        || exception instanceof NoHandlerFoundException) {
      return ApiErrorCode.RESOURCE_NOT_FOUND;
    }
    if (exception instanceof HttpRequestMethodNotSupportedException) {
      return ApiErrorCode.METHOD_NOT_ALLOWED;
    }
    if (exception instanceof HttpMediaTypeNotSupportedException) {
      return ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
    }
    if (exception instanceof HttpMediaTypeNotAcceptableException) {
      return ApiErrorCode.NOT_ACCEPTABLE;
    }
    if (exception instanceof MaxUploadSizeExceededException) {
      return ApiErrorCode.PAYLOAD_TOO_LARGE;
    }
    if (exception instanceof AsyncRequestTimeoutException) {
      return ApiErrorCode.REQUEST_TIMEOUT;
    }
    if (exception instanceof ConversionNotSupportedException
        || exception instanceof HttpMessageNotWritableException
        || exception instanceof MissingPathVariableException) {
      return ApiErrorCode.INTERNAL_SERVER_ERROR;
    }
    return ApiErrorCode.forHttpStatus(status.value());
  }

  /** Extracts safe validation paths and messages while deliberately excluding rejected values. */
  private static List<ApiValidationViolation> validationViolations(Exception exception) {
    if (exception instanceof MethodArgumentNotValidException methodArgumentNotValid) {
      List<ApiValidationViolation> violations = new ArrayList<>();
      methodArgumentNotValid
          .getBindingResult()
          .getFieldErrors()
          .forEach(
              error ->
                  violations.add(
                      new ApiValidationViolation(
                          error.getField(), safeMessage(error.getDefaultMessage()))));
      methodArgumentNotValid
          .getBindingResult()
          .getGlobalErrors()
          .forEach(
              error ->
                  violations.add(
                      new ApiValidationViolation(
                          error.getObjectName(), safeMessage(error.getDefaultMessage()))));
      return List.copyOf(violations);
    }
    if (exception instanceof HandlerMethodValidationException methodValidation) {
      return methodValidation.getParameterValidationResults().stream()
          .flatMap(
              result ->
                  result.getResolvableErrors().stream()
                      .map(
                          error ->
                              new ApiValidationViolation(
                                  parameterName(result.getMethodParameter().getParameterName()),
                                  safeMessage(error.getDefaultMessage()))))
          .toList();
    }
    return List.of();
  }

  /** Supplies a deterministic fallback when Java parameter metadata is unavailable. */
  private static String parameterName(String name) {
    return name == null || name.isBlank() ? "requestParameter" : name;
  }

  /** Supplies a generic validation message if a constraint did not define one. */
  private static String safeMessage(String message) {
    return message == null || message.isBlank() ? "is invalid" : message;
  }

  /** Extracts the current servlet request from Spring MVC's request abstraction. */
  private static HttpServletRequest servletRequest(WebRequest webRequest) {
    if (webRequest instanceof ServletWebRequest servletWebRequest) {
      return servletWebRequest.getRequest();
    }
    throw new IllegalStateException("Servlet request is required for API error handling");
  }

  /** Creates an application/problem+json response with fresh headers. */
  private ResponseEntity<Object> response(
      HttpStatusCode status,
      ApiErrorCode errorCode,
      String detail,
      HttpServletRequest request,
      List<ApiValidationViolation> violations) {
    return response(status, errorCode, detail, request, violations, HttpHeaders.EMPTY);
  }

  /** Creates an application/problem+json response while preserving framework response headers. */
  private ResponseEntity<Object> response(
      HttpStatusCode status,
      ApiErrorCode errorCode,
      String detail,
      HttpServletRequest request,
      List<ApiValidationViolation> violations,
      HttpHeaders sourceHeaders) {
    HttpHeaders headers = new HttpHeaders();
    headers.putAll(sourceHeaders);
    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return ResponseEntity.status(status)
        .headers(headers)
        .body(ApiProblemFactory.create(status, errorCode, detail, request, violations));
  }

  /** Creates the base headers for exception-handler responses. */
  private static HttpHeaders problemHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return headers;
  }
}
