package com.example.entrasso.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Tests configuration-driven endpoint role authorization and its startup constraints. */
class RoleAuthorizationServiceTest {

  /** Verifies each named authorization method uses only its corresponding configured role set. */
  @Test
  void evaluatesConfiguredRolesForEachEndpoint() {
    RoleAuthorizationService authorizationService = new RoleAuthorizationService(properties());
    var user = authenticatedAs("APP_USER");
    var manager = authenticatedAs("APP_MANAGER");
    var administrator = authenticatedAs("APP_ADMIN");

    assertThat(authorizationService.canViewDashboard(user)).isTrue();
    assertThat(authorizationService.canViewReports(user)).isFalse();
    assertThat(authorizationService.canViewAdminUsers(user)).isFalse();

    assertThat(authorizationService.canViewDashboard(manager)).isTrue();
    assertThat(authorizationService.canViewReports(manager)).isTrue();
    assertThat(authorizationService.canViewAdminUsers(manager)).isFalse();

    assertThat(authorizationService.canViewDashboard(administrator)).isTrue();
    assertThat(authorizationService.canViewReports(administrator)).isTrue();
    assertThat(authorizationService.canViewAdminUsers(administrator)).isTrue();
  }

  /** Verifies missing, unauthenticated, and unrelated principals are denied rather than failing. */
  @Test
  void deniesPrincipalsWithoutAConfiguredRole() {
    RoleAuthorizationService authorizationService = new RoleAuthorizationService(properties());
    var unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated("user", "password");

    assertThat(authorizationService.canViewDashboard(null)).isFalse();
    assertThat(authorizationService.canViewDashboard(unauthenticated)).isFalse();
    assertThat(authorizationService.canViewDashboard(authenticatedAs("UNRELATED_ROLE"))).isFalse();
  }

  /** Verifies authorization follows an external role value without changing application code. */
  @Test
  void honorsAChangedRoleValueFromConfiguration() {
    var configuredProperties =
        new EntraSecurityProperties(
            "access_as_user",
            List.of("http://localhost:5173"),
            new EntraSecurityProperties.EndpointRoles(
                Set.of("ORG_DASHBOARD_READER"), Set.of("ORG_REPORT_READER"), Set.of("ORG_ADMIN")),
            Map.of());
    RoleAuthorizationService authorizationService =
        new RoleAuthorizationService(configuredProperties);

    assertThat(authorizationService.canViewDashboard(authenticatedAs("ORG_DASHBOARD_READER")))
        .isTrue();
    assertThat(authorizationService.canViewDashboard(authenticatedAs("APP_USER"))).isFalse();
  }

  /** Verifies an omitted endpoint role list is converted to empty and rejected by validation. */
  @Test
  void rejectsMissingEndpointRoleConfiguration() {
    var endpointRoles = new EntraSecurityProperties.EndpointRoles(null, null, null);
    var properties =
        new EntraSecurityProperties(
            "access_as_user", List.of("http://localhost:5173"), endpointRoles, Map.of());

    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      assertThat(validatorFactory.getValidator().validate(properties))
          .extracting(violation -> violation.getPropertyPath().toString())
          .containsExactlyInAnyOrder(
              "endpointRoles.dashboard", "endpointRoles.reports", "endpointRoles.adminUsers");
    }
  }

  /** Creates an authenticated principal with the same authority prefix used by JWT conversion. */
  private static UsernamePasswordAuthenticationToken authenticatedAs(String role) {
    return UsernamePasswordAuthenticationToken.authenticated(
        "user", "not-used", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  /** Creates representative immutable endpoint-role configuration for the unit tests. */
  private static EntraSecurityProperties properties() {
    return new EntraSecurityProperties(
        "access_as_user",
        List.of("http://localhost:5173"),
        new EntraSecurityProperties.EndpointRoles(
            Set.of("APP_USER", "APP_MANAGER", "APP_ADMIN"),
            Set.of("APP_MANAGER", "APP_ADMIN"),
            Set.of("APP_ADMIN")),
        Map.of());
  }
}
