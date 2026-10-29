package dev.localllm.harness.application.model;

/**
 * Optional capabilities a local model may declare.
 */
public enum ModelCapability {
  /** The model can reason before answering. */
  THINKING,
  /** The harness can offer tools to the model, natively or through a text protocol. */
  TOOLS
}
