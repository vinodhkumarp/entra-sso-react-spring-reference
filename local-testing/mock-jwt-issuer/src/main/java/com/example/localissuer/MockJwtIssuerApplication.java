package com.example.localissuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the standalone local-only mock JWT issuer. */
@SpringBootApplication
public class MockJwtIssuerApplication {

  /** Starts the issuer on its loopback-only HTTP port. */
  public static void main(String[] args) {
    SpringApplication.run(MockJwtIssuerApplication.class, args);
  }
}
