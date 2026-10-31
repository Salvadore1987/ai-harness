package dev.localllm.harness.application.model;

/**
 * LLM providers the harness is built with; the active one is chosen at runtime.
 */
public enum LlmProvider {
  /** Local models served by Ollama. */
  OLLAMA,
  /** Claude models of the Anthropic API. */
  ANTHROPIC
}
