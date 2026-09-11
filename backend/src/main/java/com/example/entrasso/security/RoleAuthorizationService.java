package com.example.entrasso.security;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Evaluates endpoint access against the application-role allow-lists loaded from configuration. */
@Component("roleAuthorization")
public class RoleAuthorizationService {

  private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

  private final EntraSecurityProperties properties;

  /** Creates the authorization service from validated, immutable security configuration. */
  public RoleAuthorizationService(EntraSecurityProperties properties) {
    this.properties = properties;
  }

  /** Returns whether the current principal has any role configured for the dashboard endpoint. */
  public boolean canViewDashboard(Authentication authentication) {
    return hasAnyConfiguredRole(authentication, properties.endpointRoles().dashboard());
  }

  /** Returns whether the current principal has any role configured for the reports endpoint. */
  public boolean canViewReports(Authentication authentication) {
    return hasAnyConfiguredRole(authentication, properties.endpointRoles().reports());
  }

  /** Returns whether the current principal has any role configured for the admin-users endpoint. */
  public boolean canViewAdminUsers(Authentication authentication) {
    return hasAnyConfiguredRole(authentication, properties.endpointRoles().adminUsers());
  }

  /**
   * Compares configured Entra role values with Spring authorities produced by the JWT converter.
   *
   * <p>For example, the configured value {@code APP_ADMIN} matches the Spring authority {@code
   * ROLE_APP_ADMIN}. Missing or unauthenticated principals are denied.
   */
  private static boolean hasAnyConfiguredRole(
      Authentication authentication, Collection<String> configuredRoles) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    Set<String> grantedAuthorities =
        authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .collect(Collectors.toUnmodifiableSet());

    return configuredRoles.stream()
        .map(role -> ROLE_AUTHORITY_PREFIX + role)
        .anyMatch(grantedAuthorities::contains);
  }
}
