package com.example.entrasso.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated authorization, browser-origin, and role-to-functionality settings.
 *
 * @param requiredScope delegated Entra scope required at the HTTP boundary
 * @param allowedOrigins exact browser origins allowed by CORS
 * @param roleFunctionalities UI functionality names contributed by each Entra app role
 */
@Validated
@ConfigurationProperties("app.security")
public record EntraSecurityProperties(
    @NotBlank String requiredScope,
    @NotEmpty List<@NotBlank String> allowedOrigins,
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
}
