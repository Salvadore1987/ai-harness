package dev.localllm.harness.application.port.out;

/**
 * Tells how large the context window of a model actually is.
 */
public interface ContextBudgetPort {

  /**
   * Returns the context window the model is loaded with, or the configured one if it is not
   * loaded.
   *
   * @param model model tag
   * @return context window in tokens
   */
  int contextLength(String model);
}
