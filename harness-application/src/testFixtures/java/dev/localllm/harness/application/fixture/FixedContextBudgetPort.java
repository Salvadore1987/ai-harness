package dev.localllm.harness.application.fixture;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.port.out.ContextBudgetPort;

/**
 * Context window of a fixed size for every model of every provider.
 */
public final class FixedContextBudgetPort implements ContextBudgetPort {

  private int contextLength;

  /**
   * Creates the fake.
   *
   * @param contextLength context window in tokens
   */
  public FixedContextBudgetPort(int contextLength) {
    this.contextLength = contextLength;
  }

  /**
   * Changes the reported context window.
   *
   * @param newContextLength context window in tokens
   */
  public void setContextLength(int newContextLength) {
    this.contextLength = newContextLength;
  }

  @Override
  public int contextLength(LlmProvider provider, String model) {
    return contextLength;
  }
}
