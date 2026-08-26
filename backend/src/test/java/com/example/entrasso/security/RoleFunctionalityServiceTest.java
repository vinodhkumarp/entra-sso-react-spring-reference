package com.example.entrasso.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests the UI functionality mapping built from validated Entra application roles. */
class RoleFunctionalityServiceTest {

  /** Verifies multiple roles produce a sorted, deduplicated functionality set. */
  @Test
  void resolvesConfiguredRoleFunctionalities() {
    EntraSecurityProperties properties =
        new EntraSecurityProperties(
            "access_as_user",
            List.of("http://localhost:5173"),
            Map.of(
                "APP_USER", List.of("DASHBOARD_VIEW"),
                "APP_MANAGER", List.of("DASHBOARD_VIEW", "REPORTS_VIEW")));

    var result =
        new RoleFunctionalityService(properties)
            .resolve(List.of("APP_USER", "APP_MANAGER", "UNKNOWN_ROLE"));

    assertThat(result).containsExactly("DASHBOARD_VIEW", "REPORTS_VIEW");
  }
}
