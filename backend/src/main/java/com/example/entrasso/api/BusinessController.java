package com.example.entrasso.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Demonstrates role-authorized dashboard and reporting endpoints. */
@RestController
public class BusinessController {

    /** Returns dashboard data to user, manager, or administrator roles. */
    @GetMapping("/api/dashboard")
    @PreAuthorize("hasAnyRole('APP_USER', 'APP_MANAGER', 'APP_ADMIN')")
    public DashboardResponse dashboard() {
        return new DashboardResponse(
                "Dashboard data returned by the Entra SSO backend",
                List.of("Open tasks: 4", "Reports ready: 2"),
                Instant.now());
    }

    /** Returns report summaries only to manager and administrator roles. */
    @GetMapping("/api/reports")
    @PreAuthorize("hasAnyRole('APP_MANAGER', 'APP_ADMIN')")
    public ReportsResponse reports() {
        return new ReportsResponse(
                "Reports data returned by the Entra SSO backend",
                List.of("monthly-summary.csv", "security-audit.pdf"),
                Instant.now());
    }

    /** Example dashboard API response. */
    public record DashboardResponse(String message, List<String> widgets, Instant generatedAt) {
    }

    /** Example reports API response. */
    public record ReportsResponse(String message, List<String> reports, Instant generatedAt) {
    }
}
