package com.example.localissuer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Verifies discovery, public-key publication, token claims, signatures, validation, and CORS. */
@SpringBootTest
@AutoConfigureMockMvc
class LocalIssuerApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  /** Verifies Spring Resource Server can discover the local issuer's public-key location. */
  @Test
  void publishesOidcDiscoveryAndJwks() throws Exception {
    mockMvc
        .perform(get("/.well-known/openid-configuration"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuer").value("http://localhost:9090"))
        .andExpect(jsonPath("$.jwks_uri").value("http://localhost:9090/oauth2/jwks"));
    mockMvc
        .perform(get("/oauth2/jwks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
        .andExpect(jsonPath("$.keys[0].d").doesNotExist());
  }

  /** Verifies an issued manager token has the intended claims and a matching RS256 signature. */
  @Test
  void issuesVerifiableManagerToken() throws Exception {
    String tokenResponse =
        mockMvc
            .perform(
                post("/test-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":\"manager\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(300))
            .andExpect(jsonPath("$.user.role").value("APP_MANAGER"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode tokenJson = objectMapper.readTree(tokenResponse);
    SignedJWT token = SignedJWT.parse(tokenJson.required("accessToken").asString());

    String jwksResponse =
        mockMvc
            .perform(get("/oauth2/jwks"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var publicKey =
        JWKSet.parse(jwksResponse).getKeyByKeyId(token.getHeader().getKeyID()).toRSAKey();

    org.junit.jupiter.api.Assertions.assertTrue(
        token.verify(new RSASSAVerifier(publicKey.toRSAPublicKey())));
    org.junit.jupiter.api.Assertions.assertEquals(
        "http://localhost:9090", token.getJWTClaimsSet().getIssuer());
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.List.of("local-spring-api"), token.getJWTClaimsSet().getAudience());
    org.junit.jupiter.api.Assertions.assertEquals(
        "access_as_user", token.getJWTClaimsSet().getStringClaim("scp"));
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.List.of("APP_MANAGER"), token.getJWTClaimsSet().getStringListClaim("roles"));
  }

  /** Verifies arbitrary callers cannot mint roles or identities outside the fixed catalog. */
  @Test
  void rejectsUnknownIdentity() throws Exception {
    mockMvc
        .perform(
            post("/test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"super-admin\"}"))
        .andExpect(status().isBadRequest());
  }

  /** Verifies only the standalone local test UI origin can request tokens from a browser. */
  @Test
  void permitsConfiguredTestUiCorsOrigin() throws Exception {
    mockMvc
        .perform(
            options("/test-token")
                .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174"))
        .andExpect(
            header()
                .string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")));
  }
}
