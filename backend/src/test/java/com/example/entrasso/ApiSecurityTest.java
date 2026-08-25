package com.example.entrasso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real filter chain and method authorization across every API flow. */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
        "spring.security.oauth2.resourceserver.jwt.audiences=test-api"
})
@Import(ApiSecurityTest.DecoderConfiguration.class)
class ApiSecurityTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    /** Builds MockMvc with the application's actual Spring Security filter chain. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Verifies a bearer token is mandatory for all protected API endpoints. */
    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    /** Verifies the delegated API scope is mandatory even when an app role is present. */
    @Test
    void rejectsTokenWithoutRequiredScope() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APP_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Verifies an ordinary application user can load only the dashboard flow. */
    @Test
    void appliesApplicationUserRole() throws Exception {
        var userToken = jwt()
                .jwt(token -> token
                        .claim("oid", "user-1")
                        .claim("preferred_username", "user@example.test")
                        .claim("name", "Example User")
                        .claim("tid", "tenant-1")
                        .claim("roles", List.of("APP_USER")))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_access_as_user"),
                        new SimpleGrantedAuthority("ROLE_APP_USER"));

        mockMvc.perform(get("/api/dashboard").with(userToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports").with(userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").with(userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me").with(userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("APP_USER"))
                .andExpect(jsonPath("$.functionalities[0]").value("DASHBOARD_VIEW"));
    }

    /** Verifies a manager can load dashboard and report data but not administrator data. */
    @Test
    void appliesManagerRole() throws Exception {
        var managerToken = jwt().authorities(
                new SimpleGrantedAuthority("SCOPE_access_as_user"),
                new SimpleGrantedAuthority("ROLE_APP_MANAGER"));

        mockMvc.perform(get("/api/dashboard").with(managerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports").with(managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0]").value("monthly-summary.csv"));
        mockMvc.perform(get("/api/admin/users").with(managerToken))
                .andExpect(status().isForbidden());
    }

    /** Verifies an application administrator can call every example endpoint. */
    @Test
    void allowsAdministratorToUseEveryFlow() throws Exception {
        var administratorToken = jwt()
                .jwt(token -> token
                        .claim("oid", "admin-1")
                        .claim("preferred_username", "admin@example.test")
                        .claim("roles", List.of("APP_ADMIN")))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_access_as_user"),
                        new SimpleGrantedAuthority("ROLE_APP_ADMIN"));

        mockMvc.perform(get("/api/dashboard").with(administratorToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports").with(administratorToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").with(administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("demo-user-1"));
        mockMvc.perform(get("/api/me").with(administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.functionalities", containsInAnyOrder(
                        "DASHBOARD_VIEW", "REPORTS_VIEW", "ADMIN_USERS_VIEW")));
    }

    /** Verifies the configured React origin can complete an unauthenticated CORS preflight. */
    @Test
    void allowsConfiguredCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/dashboard")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"));
    }

    /** Supplies a local decoder so tests never call Microsoft Entra endpoints. */
    @TestConfiguration(proxyBeanMethods = false)
    static class DecoderConfiguration {

        /** Creates a placeholder decoder; SecurityMockMvc injects authenticated test JWTs. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("Decoder is not used by SecurityMockMvc JWT tests");
            };
        }
    }
}
