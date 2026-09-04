package com.example.localissuer;

import com.example.localissuer.TestIdentityCatalog.TestIdentity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Generates an ephemeral RSA key and short-lived Entra-shaped test access tokens. */
@Service
public final class LocalJwtService {

  private static final String LOCAL_TENANT_ID = "local-test-tenant";
  private static final String REQUIRED_SCOPE = "access_as_user";

  private final LocalIssuerProperties properties;
  private final Clock clock;
  private final RSAKey signingKey;

  /** Creates a new 3072-bit signing key that remains only in this process's memory. */
  public LocalJwtService(LocalIssuerProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.signingKey = generateSigningKey();
  }

  /** Returns a JWKS document containing only the public portion of the current signing key. */
  public Map<String, Object> publicJwkSet() {
    return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
  }

  /** Signs a short-lived access token for one allow-listed local identity. */
  public IssuedToken issue(TestIdentity identity) {
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(properties.tokenTtl());
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(properties.issuer())
            .audience(properties.audience())
            .subject(identity.subjectId())
            .claim("oid", identity.subjectId())
            .claim("tid", LOCAL_TENANT_ID)
            .claim("preferred_username", identity.username())
            .claim("name", identity.displayName())
            .claim("scp", REQUIRED_SCOPE)
            .claim("roles", List.of(identity.role()))
            .issueTime(Date.from(issuedAt))
            .notBeforeTime(Date.from(issuedAt.minusSeconds(5)))
            .expirationTime(Date.from(expiresAt))
            .jwtID(UUID.randomUUID().toString())
            .build();
    var token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(signingKey.getKeyID())
                .build(),
            claims);
    try {
      token.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
    } catch (JOSEException exception) {
      throw new IllegalStateException("Unable to sign a local test token", exception);
    }
    return new IssuedToken(token.serialize(), issuedAt, expiresAt);
  }

  /** Creates the ephemeral asymmetric key used for signing and JWKS publication. */
  private static RSAKey generateSigningKey() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(3072);
      KeyPair keyPair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
          .privateKey((RSAPrivateKey) keyPair.getPrivate())
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .keyID(UUID.randomUUID().toString())
          .build();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to initialize the local RSA signing key", exception);
    }
  }

  /** Signed token value and timestamps used to build the local token response. */
  public record IssuedToken(String value, Instant issuedAt, Instant expiresAt) {}
}
