package com.example.entrasso.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/** Resolves UI functionality names from validated Microsoft Entra application roles. */
@Service
public class RoleFunctionalityService {

  private final EntraSecurityProperties properties;

  /** Creates the resolver from the application's externalized role mapping. */
  public RoleFunctionalityService(EntraSecurityProperties properties) {
    this.properties = properties;
  }

  /**
   * Returns a sorted, deduplicated functionality set for the supplied app roles.
   *
   * <p>For example, {@code [APP_USER, APP_MANAGER]} resolves to {@code [DASHBOARD_VIEW,
   * REPORTS_VIEW]}.
   */
  public Set<String> resolve(Collection<String> roles) {
    Set<String> result = new TreeSet<>();
    if (roles != null) {
      roles.stream()
          .map(properties.roleFunctionalities()::get)
          .filter(functionalities -> functionalities != null)
          .forEach(result::addAll);
    }
    return Collections.unmodifiableSet(result);
  }
}
