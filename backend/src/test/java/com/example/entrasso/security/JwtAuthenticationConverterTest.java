package com.example.entrasso.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Tests conversion of validated Entra claims into Spring Security authorities. */
class JwtAuthenticationConverterTest {

  /** Verifies delegated scopes and app roles are both available to authorization rules. */
  @Test
  void combinesScopesAndRoles() {
    Instant now = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("fallback-subject")
            .claim("oid", "user-object-id")
            .claim("scp", "access_as_user reports.read")
            .claim("roles", List.of("APP_USER", "APP_MANAGER"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();

    var authentication = new EntraSecurityConfiguration().jwtAuthenticationConverter().convert(jwt);

    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("user-object-id");
    assertThat(authentication.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .contains(
            "SCOPE_access_as_user", "SCOPE_reports.read", "ROLE_APP_USER", "ROLE_APP_MANAGER");
  }
}
