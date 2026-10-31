package dev.localllm.harness.application.port.out;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * Tells how large the context window of a model actually is.
 */
public interface ContextBudgetPort {

  /**
   * Returns the context window the model is loaded with, or the configured one if it is not
   * loaded.
   *
   * @param provider provider of the model
   * @param model model tag
   * @return context window in tokens
   */
  int contextLength(LlmProvider provider, String model);
}
