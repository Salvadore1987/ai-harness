package dev.localllm.harness.application.model;

/**
 * Author of a chat message.
 */
public enum Role {
  SYSTEM,
  USER,
  ASSISTANT,
  /** Output of a tool call, sent back to the model. */
  TOOL_RESULT
}
