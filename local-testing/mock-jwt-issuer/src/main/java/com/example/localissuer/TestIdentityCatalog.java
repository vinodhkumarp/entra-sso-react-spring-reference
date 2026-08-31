package com.example.localissuer;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Contains the fixed identities and roles that the local issuer is permitted to mint. */
@Component
public class TestIdentityCatalog {

  private static final List<TestIdentity> IDENTITIES =
      List.of(
          new TestIdentity("user", "local-user", "user@local.test", "Local Test User", "APP_USER"),
          new TestIdentity(
              "manager",
              "local-manager",
              "manager@local.test",
              "Local Test Manager",
              "APP_MANAGER"),
          new TestIdentity(
              "admin", "local-admin", "admin@local.test", "Local Test Administrator", "APP_ADMIN"));

  /** Returns every predefined identity without exposing mutable catalog state. */
  public List<TestIdentity> all() {
    return IDENTITIES;
  }

  /** Resolves a test identity or returns HTTP 400 for an unsupported caller-supplied ID. */
  public TestIdentity require(String id) {
    return IDENTITIES.stream()
        .filter(identity -> identity.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unknown local test identity: " + id));
  }

  /** Public, non-sensitive identity metadata returned to the separate local test UI. */
  public record TestIdentity(
      String id, String subjectId, String username, String displayName, String role) {}
}
