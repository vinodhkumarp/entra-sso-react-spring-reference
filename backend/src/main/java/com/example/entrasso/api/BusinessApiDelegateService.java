package com.example.entrasso.api;

import com.example.entrasso.generated.api.BusinessApiDelegate;
import com.example.entrasso.generated.model.DashboardResponse;
import com.example.entrasso.generated.model.ReportsResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Supplies business behavior to the generated dashboard and reports controller. */
@Service
public class BusinessApiDelegateService implements BusinessApiDelegate {

  /** Returns dashboard data to user, manager, or administrator roles. */
  @Override
  @PreAuthorize("hasAnyRole('APP_USER', 'APP_MANAGER', 'APP_ADMIN')")
  public DashboardResponse getDashboard() {
    return new DashboardResponse(
        "Dashboard data returned by the Entra SSO backend",
        List.of("Open tasks: 4", "Reports ready: 2"),
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  /** Returns report summaries only to manager and administrator roles. */
  @Override
  @PreAuthorize("hasAnyRole('APP_MANAGER', 'APP_ADMIN')")
  public ReportsResponse getReports() {
    return new ReportsResponse(
        "Reports data returned by the Entra SSO backend",
        List.of("monthly-summary.csv", "security-audit.pdf"),
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
