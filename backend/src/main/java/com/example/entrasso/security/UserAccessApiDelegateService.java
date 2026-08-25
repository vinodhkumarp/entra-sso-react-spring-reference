package com.example.entrasso.security;

import com.example.entrasso.generated.api.UserAccessApiDelegate;
import com.example.entrasso.generated.model.UserAccessResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Supplies validated identity metadata to the generated current-user controller. */
@Service
public class UserAccessApiDelegateService implements UserAccessApiDelegate {

  private final RoleFunctionalityService functionalityService;

  /** Creates the delegate with the configured role-to-functionality resolver. */
  public UserAccessApiDelegateService(RoleFunctionalityService functionalityService) {
    this.functionalityService = functionalityService;
  }

  /**
   * Returns metadata derived only from the bearer token already validated by Spring Security.
   *
   * <p>This method does not authenticate the user and does not issue another token. React uses the
   * response to render navigation, while protected API delegates remain authoritative.
   */
  @Override
  public UserAccessResponse getCurrentUser() {
    Jwt jwt = currentJwt();
    List<String> roles =
        Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElseGet(List::of).stream()
            .sorted()
            .toList();
    String username =
        firstPresent(
            jwt.getClaimAsString("preferred_username"),
            jwt.getClaimAsString("upn"),
            jwt.getSubject());
    String subjectId = firstPresent(jwt.getClaimAsString("oid"), jwt.getSubject());
    String displayName = firstPresent(jwt.getClaimAsString("name"), username);
    String tenantId = firstPresent(jwt.getClaimAsString("tid"));
    OffsetDateTime tokenExpiresAt =
        Optional.ofNullable(jwt.getExpiresAt())
            .map(expiration -> OffsetDateTime.ofInstant(expiration, ZoneOffset.UTC))
            .orElseThrow(
                () ->
                    new AuthenticationCredentialsNotFoundException(
                        "The validated access token does not contain an expiration time"));

    return new UserAccessResponse(
        subjectId,
        username,
        displayName,
        tenantId,
        roles,
        functionalityService.resolve(roles).stream().toList(),
        tokenExpiresAt);
  }

  /** Returns the validated JWT principal stored by Spring Security for the current request. */
  private static Jwt currentJwt() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new AuthenticationCredentialsNotFoundException(
          "A validated JWT principal is required for this operation");
    }
    return jwt;
  }

  /** Returns the first non-blank claim value, or an empty string when none is available. */
  private static String firstPresent(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return "";
  }
}
