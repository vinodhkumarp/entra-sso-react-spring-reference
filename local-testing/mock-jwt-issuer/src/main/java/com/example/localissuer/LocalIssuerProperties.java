package com.example.localissuer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated local issuer, audience, lifetime, and test-UI CORS configuration. */
@Validated
@ConfigurationProperties("local.issuer")
public record LocalIssuerProperties(
    @NotBlank String issuer,
    @NotBlank String audience,
    @NotNull Duration tokenTtl,
    @NotEmpty List<@NotBlank String> allowedOrigins) {

  /** Validates the local token lifetime and makes the CORS origin list immutable. */
  public LocalIssuerProperties {
    if (tokenTtl != null
        && (tokenTtl.compareTo(Duration.ofSeconds(30)) < 0
            || tokenTtl.compareTo(Duration.ofMinutes(15)) > 0)) {
      throw new IllegalArgumentException("local.issuer.token-ttl must be between 30s and 15m");
    }
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
