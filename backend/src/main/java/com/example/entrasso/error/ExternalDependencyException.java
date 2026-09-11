package com.example.entrasso.error;

import java.io.Serial;
import java.util.Objects;

/** Wraps technology-specific failures at the application's infrastructure boundary. */
public final class ExternalDependencyException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final DependencyType dependencyType;

  /**
   * Creates an infrastructure failure whose original cause is retained for internal logging.
   *
   * <p>For example, a future Kafka or SFTP adapter should catch its client-library exception and
   * wrap it here. The message is logged internally and is never returned to the API caller.
   */
  public ExternalDependencyException(
      DependencyType dependencyType, String message, Throwable cause) {
    super(message, cause);
    this.dependencyType = Objects.requireNonNull(dependencyType, "dependencyType is required");
  }

  /** Returns the dependency category used to choose the public status and error code. */
  public DependencyType dependencyType() {
    return dependencyType;
  }

  /** Identifies future infrastructure types without introducing their client dependencies now. */
  public enum DependencyType {
    DATABASE,
    DOWNSTREAM_API,
    MESSAGE_BROKER,
    FILE_TRANSFER
  }
}
