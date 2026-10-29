package dev.localllm.harness.application.model;

import com.fasterxml.uuid.Generators;

/**
 * A tool invocation requested by the model.
 *
 * @param id identifier linking the call to its result; taken from the model response or
 *     generated
 * @param name tool name
 * @param argumentsJson arguments as a JSON object
 */
public record ToolCall(String id, String name, String argumentsJson) {

  /**
   * Generates an identifier for a call the model sent without one.
   *
   * @return a fresh UUIDv7 text
   */
  public static String newId() {
    return Generators.timeBasedEpochGenerator().generate().toString();
  }
}
