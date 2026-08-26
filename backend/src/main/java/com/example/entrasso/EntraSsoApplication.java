package com.example.entrasso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the single Microsoft Entra-secured backend application. */
@SpringBootApplication
public class EntraSsoApplication {

  /** Launches the API and its embedded web server. */
  public static void main(String[] args) {
    SpringApplication.run(EntraSsoApplication.class, args);
  }
}
