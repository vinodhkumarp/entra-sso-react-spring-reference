package com.example.entrasso.api;

import com.example.entrasso.generated.api.AdminUsersApiDelegate;
import com.example.entrasso.generated.model.AdminUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Supplies administrator behavior to the generated admin-users controller. */
@Service
public class AdminUsersApiDelegateService implements AdminUsersApiDelegate {

  /** Returns safe demonstration user summaries only to the Entra {@code APP_ADMIN} role. */
  @Override
  @PreAuthorize("hasRole('APP_ADMIN')")
  public List<AdminUser> getAdminUsers() {
    return List.of(
        new AdminUser("demo-user-1", "Demo User", "ACTIVE"),
        new AdminUser("demo-admin-1", "Demo Administrator", "ACTIVE"));
  }
}
