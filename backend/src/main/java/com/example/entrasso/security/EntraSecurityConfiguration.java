package com.example.entrasso.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Configures the application as a stateless Microsoft Entra JWT resource server. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(EntraSecurityProperties.class)
public class EntraSecurityConfiguration {

    /**
     * Creates the filter chain that validates bearer tokens before protected controllers execute.
     *
     * <p>Spring Boot creates the {@code JwtDecoder} from the configured issuer and audience. That
     * decoder validates the JWT signature, issuer, audience, expiry, and not-before time.</p>
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            EntraSecurityProperties properties,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error")
                                .permitAll()
                        .requestMatchers("/api/**")
                                .hasAuthority("SCOPE_" + properties.requiredScope())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /** Converts Entra's space-delimited {@code scp} and array-valued {@code roles} claims. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scp");
        scopes.setAuthorityPrefix("SCOPE_");

        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("oid");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> combine(jwt, scopes, roles));
        return converter;
    }

    /** Creates the exact browser-origin allow-list used by Spring Security CORS processing. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(EntraSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("WWW-Authenticate"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Combines optional scope and role converter output into one immutable authority collection. */
    private static Collection<GrantedAuthority> combine(
            Jwt jwt,
            JwtGrantedAuthoritiesConverter scopes,
            JwtGrantedAuthoritiesConverter roles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        addIfPresent(authorities, scopes.convert(jwt));
        addIfPresent(authorities, roles.convert(jwt));
        return List.copyOf(authorities);
    }

    /** Adds converter output only when its token claim is present. */
    private static void addIfPresent(
            Collection<GrantedAuthority> destination,
            Collection<GrantedAuthority> source) {
        if (source != null) {
            destination.addAll(source);
        }
    }
}

