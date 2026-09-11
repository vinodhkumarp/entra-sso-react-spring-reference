package com.example.entrasso.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaces the servlet container's fallback error page with the application's JSON contract. */
@RestController
public final class ApiErrorController implements ErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiErrorController.class);

  /** Handles any error dispatch that was not resolved by Spring Security or MVC advice. */
  @RequestMapping("${server.error.path:${error.path:/error}}")
  public ResponseEntity<Object> handleError(HttpServletRequest request) {
    int statusValue = errorStatus(request);
    HttpStatusCode status = HttpStatusCode.valueOf(statusValue);
    Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    if (status.is5xxServerError()) {
      LOGGER.error("Unhandled servlet error dispatch with status={}", statusValue, exception);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return ResponseEntity.status(status)
        .headers(headers)
        .body(ApiProblemFactory.create(status, ApiErrorCode.forHttpStatus(statusValue), request));
  }

  /** Uses a valid error-dispatch status and otherwise fails safely as HTTP 500. */
  private static int errorStatus(HttpServletRequest request) {
    Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    if (value instanceof Number number && number.intValue() >= 400 && number.intValue() <= 599) {
      return number.intValue();
    }
    return HttpStatus.INTERNAL_SERVER_ERROR.value();
  }
}
