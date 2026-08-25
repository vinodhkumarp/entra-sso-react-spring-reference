package com.example.localissuer;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Provides time and exact-origin CORS dependencies for the local issuer. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalIssuerProperties.class)
public class LocalIssuerConfiguration {

  /** Supplies UTC wall-clock time to token creation and expiration calculations. */
  @Bean
  Clock issuerClock() {
    return Clock.systemUTC();
  }

  /** Allows only the separately hosted local test UI to request test identities and tokens. */
  @Bean
  WebMvcConfigurer localIssuerCors(LocalIssuerProperties properties) {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type")
            .allowCredentials(false)
            .maxAge(3600);
      }
    };
  }
}
