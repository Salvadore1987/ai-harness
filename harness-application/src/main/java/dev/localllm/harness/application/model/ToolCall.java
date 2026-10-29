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
   * Name of the call that stands for a tool call the model wrote but the harness could not
   * read; it is stored in the history so that the error result has a call to answer.
   */
  public static final String PARSE_ERROR = "parse_error";

  private static final String NO_ARGUMENTS = "{}";

  /**
   * Creates the call that stands for an unreadable tool call.
   *
   * @return call named {@link #PARSE_ERROR}
   */
  public static ToolCall parseError() {
    return new ToolCall(newId(), PARSE_ERROR, NO_ARGUMENTS);
  }

  /**
   * Generates an identifier for a call the model sent without one.
   *
   * @return a fresh UUIDv7 text
   */
  public static String newId() {
    return Generators.timeBasedEpochGenerator().generate().toString();
  }
}
