package dev.localllm.harness.application.model;

/**
 * Optional capabilities a local model may declare.
 */
public enum ModelCapability {
  /** The model can reason before answering. */
  THINKING,
  /** The model supports native tool calling. */
  TOOLS
}
