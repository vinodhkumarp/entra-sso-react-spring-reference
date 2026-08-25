package com.example.entrasso.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Exposes the authenticated user's identity, roles, and available UI functionality. */
@RestController
public class UserAccessController {

    private final RoleFunctionalityService functionalityService;

    /** Creates the endpoint with the configured role-to-functionality resolver. */
    public UserAccessController(RoleFunctionalityService functionalityService) {
        this.functionalityService = functionalityService;
    }

    /**
     * Returns metadata derived only from the already validated access token.
     *
     * <p>This endpoint does not authenticate the user and does not issue another token. React uses
     * the response to render navigation, while protected controller methods remain authoritative.</p>
     */
    @GetMapping("/api/me")
    public UserAccessResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles"))
                .orElseGet(List::of)
                .stream()
                .sorted()
                .toList();
        String username = firstPresent(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("upn"),
                jwt.getSubject());
        String subjectId = firstPresent(jwt.getClaimAsString("oid"), jwt.getSubject());
        String displayName = firstPresent(jwt.getClaimAsString("name"), username);

        return new UserAccessResponse(
                subjectId,
                username,
                displayName,
                jwt.getClaimAsString("tid"),
                roles,
                functionalityService.resolve(roles).stream().toList(),
                jwt.getExpiresAt());
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

    /** Response consumed by React to render identity and navigation state. */
    public record UserAccessResponse(
            String subjectId,
            String username,
            String displayName,
            String tenantId,
            List<String> roles,
            List<String> functionalities,
            Instant tokenExpiresAt) {
    }
}

