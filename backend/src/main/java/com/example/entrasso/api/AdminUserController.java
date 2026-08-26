package com.example.entrasso.api;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demonstrates an administrator-only endpoint in the same backend application. */
@RestController
public class AdminUserController {

  /** Returns safe demonstration user summaries only to the Entra {@code APP_ADMIN} role. */
  @GetMapping("/api/admin/users")
  @PreAuthorize("hasRole('APP_ADMIN')")
  public List<UserSummary> users() {
    return List.of(
        new UserSummary("demo-user-1", "Demo User", "ACTIVE"),
        new UserSummary("demo-admin-1", "Demo Administrator", "ACTIVE"));
  }

  /** Example response; a real service would load this projection from its own data source. */
  public record UserSummary(String id, String displayName, String status) {}
}
