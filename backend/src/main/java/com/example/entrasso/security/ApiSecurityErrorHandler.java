package com.example.entrasso.security;

import com.example.entrasso.error.ApiErrorCode;
import com.example.entrasso.error.ApiProblemFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

/** Writes custom JSON responses for authentication and authorization failures. */
@Component
public final class ApiSecurityErrorHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final BearerTokenAuthenticationEntryPoint bearerAuthenticationEntryPoint =
      new BearerTokenAuthenticationEntryPoint();
  private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler =
      new BearerTokenAccessDeniedHandler();
  private final ObjectWriter problemWriter;

  /**
   * Creates the handler with an immutable writer snapshot of Spring's JSON configuration.
   *
   * <p>Jackson writers are thread-safe, use immutable configuration, and are created as new
   * instances. The handler therefore does not retain the externally managed mutable mapper.
   */
  public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
    this.problemWriter = objectMapper.writerFor(org.springframework.http.ProblemDetail.class);
  }

  /** Preserves the standards-compliant bearer challenge and replaces Spring's body for HTTP 401. */
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    bearerAuthenticationEntryPoint.commence(request, response, authenticationException);
    ApiProblemFactory.write(
        response,
        HttpStatus.UNAUTHORIZED,
        ApiErrorCode.AUTHENTICATION_REQUIRED,
        request,
        problemWriter);
  }

  /** Preserves bearer authorization headers and writes the application's HTTP 403 problem body. */
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
    ApiProblemFactory.write(
        response, HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, request, problemWriter);
  }
}
