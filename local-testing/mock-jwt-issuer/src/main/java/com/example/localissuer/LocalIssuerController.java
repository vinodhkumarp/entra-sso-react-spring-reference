package com.example.localissuer;

import com.example.localissuer.LocalJwtService.IssuedToken;
import com.example.localissuer.TestIdentityCatalog.TestIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Publishes discovery/JWKS metadata and issues tokens for fixed local test identities. */
@RestController
public class LocalIssuerController {

  private final LocalIssuerProperties properties;
  private final LocalJwtService jwtService;
  private final TestIdentityCatalog identities;

  /** Creates the HTTP boundary around the local issuer and identity catalog. */
  public LocalIssuerController(
      LocalIssuerProperties properties,
      LocalJwtService jwtService,
      TestIdentityCatalog identities) {
    this.properties = properties;
    this.jwtService = jwtService;
    this.identities = identities;
  }

  /** Returns the minimal OIDC discovery metadata required by Spring's issuer-based decoder. */
  @GetMapping("/.well-known/openid-configuration")
  public Map<String, Object> discovery() {
    return Map.of(
        "issuer", properties.issuer(),
        "jwks_uri", properties.issuer() + "/oauth2/jwks",
        "response_types_supported", List.of("token"),
        "subject_types_supported", List.of("public"),
        "id_token_signing_alg_values_supported", List.of("RS256"));
  }

  /** Returns the public RSA key used by the backend to validate local test-token signatures. */
  @GetMapping("/oauth2/jwks")
  public Map<String, Object> jwks() {
    return jwtService.publicJwkSet();
  }

  /** Returns the allow-listed identities available to the separate local test UI. */
  @GetMapping("/test-users")
  public List<TestIdentity> users() {
    return identities.all();
  }

  /** Issues a short-lived bearer token for one allow-listed local identity. */
  @PostMapping("/test-token")
  public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
    TestIdentity identity = identities.require(request.userId());
    IssuedToken token = jwtService.issue(identity);
    var response =
        new TokenResponse(
            token.value(),
            "Bearer",
            token.expiresAt().getEpochSecond() - token.issuedAt().getEpochSecond(),
            token.issuedAt(),
            token.expiresAt(),
            identity);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(response);
  }

  /** Validated local token request. */
  public record TokenRequest(@NotBlank String userId) {}

  /** Local-only token response consumed by the test UI or command-line tools. */
  public record TokenResponse(
      String accessToken,
      String tokenType,
      long expiresIn,
      Instant issuedAt,
      Instant expiresAt,
      TestIdentity user) {}
}
