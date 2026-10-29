package dev.localllm.harness.infrastructure.in.cli;

/**
 * How user messages are handled.
 */
public enum ReplMode {
  /** Plain chat: the model answers without tools. */
  CHAT,
  /** Agent loop: the model may call tools before answering. */
  AGENT
}
