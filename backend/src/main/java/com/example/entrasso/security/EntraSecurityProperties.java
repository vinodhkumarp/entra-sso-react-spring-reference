package com.example.entrasso.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated endpoint authorization, browser-origin, and role-to-functionality settings.
 *
 * @param requiredScope delegated Entra scope required at the HTTP boundary
 * @param allowedOrigins exact browser origins allowed by CORS
 * @param endpointRoles application roles allowed to call each protected example endpoint
 * @param roleFunctionalities UI functionality names contributed by each Entra app role
 */
@Validated
@ConfigurationProperties("app.security")
public record EntraSecurityProperties(
    @NotBlank String requiredScope,
    @NotEmpty List<@NotBlank String> allowedOrigins,
    @Valid @NotNull EndpointRoles endpointRoles,
    Map<String, List<String>> roleFunctionalities) {

  /** Creates immutable defensive copies so configuration cannot change after startup. */
  public EntraSecurityProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    roleFunctionalities =
        roleFunctionalities == null
            ? Map.of()
            : roleFunctionalities.entrySet().stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }

  /**
   * Defines the externally configurable role allow-list for each protected example endpoint.
   *
   * @param dashboard roles allowed to read dashboard data
   * @param reports roles allowed to read reports
   * @param adminUsers roles allowed to read administrator user data
   */
  public record EndpointRoles(
      @NotEmpty Set<@NotBlank String> dashboard,
      @NotEmpty Set<@NotBlank String> reports,
      @NotEmpty Set<@NotBlank String> adminUsers) {

    /** Creates immutable role sets and lets validation report any omitted or empty allow-list. */
    public EndpointRoles {
      dashboard = dashboard == null ? Set.of() : Set.copyOf(dashboard);
      reports = reports == null ? Set.of() : Set.copyOf(reports);
      adminUsers = adminUsers == null ? Set.of() : Set.copyOf(adminUsers);
    }
  }
}
