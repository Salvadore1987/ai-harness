package dev.localllm.harness.application.model;

/**
 * How a model turn ended.
 */
public enum TurnStatus {
  /** The model finished the answer. */
  COMPLETED,
  /** The user cancelled the generation. */
  CANCELLED,
  /** The stream broke after part of the answer had arrived. */
  INTERRUPTED
}
