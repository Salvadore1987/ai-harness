package dev.localllm.harness.application.model;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.uuid.Generators;

/**
 * Identifier of a chat session, backed by a time-ordered UUIDv7.
 *
 * @param value the UUIDv7 value
 */
public record SessionId(UUID value) {

  /**
   * Validates the identifier value.
   *
   * @param value the UUIDv7 value
   */
  public SessionId {
    Objects.requireNonNull(value, "value");
  }

  /**
   * Generates a new session identifier.
   *
   * @return a fresh UUIDv7-based identifier
   */
  public static SessionId newId() {
    return new SessionId(Generators.timeBasedEpochGenerator().generate());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
