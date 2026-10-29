package dev.localllm.harness.application.service;

import java.util.List;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.model.ContextUsage;
import dev.localllm.harness.application.port.out.ContextBudgetPort;

/**
 * Estimates how much of the model context window a history takes.
 *
 * <p>The estimate divides the characters by {@link ContextPolicy#charsPerToken()} and adds a
 * fixed overhead per message for the chat template. Ollama's {@code prompt_eval_count} is not
 * used for calibration because it counts only the tokens not served from the prompt cache.
 */
public class ContextMeter {

  private static final int MESSAGE_OVERHEAD_TOKENS = 4;
  private static final int PERCENT = 100;

  private final ContextBudgetPort contextBudgetPort;
  private final ContextPolicy policy;

  /**
   * Creates the meter.
   *
   * @param contextBudgetPort actual context window of the model
   * @param policy estimation and compaction rules
   */
  public ContextMeter(ContextBudgetPort contextBudgetPort, ContextPolicy policy) {
    this.contextBudgetPort = contextBudgetPort;
    this.policy = policy;
  }

  /**
   * Estimates the tokens of a history.
   *
   * @param messages history to estimate
   * @return estimated tokens
   */
  public int estimateTokens(List<ChatMessage> messages) {
    long chars = messages.stream().mapToLong(message -> message.content().length()).sum();
    return (int) Math.ceil(chars / policy.charsPerToken())
        + MESSAGE_OVERHEAD_TOKENS * messages.size();
  }

  /**
   * Measures a history against the context window of a model.
   *
   * @param messages history sent with the next request
   * @param model model tag
   * @return estimated usage
   */
  public ContextUsage measure(List<ChatMessage> messages, String model) {
    int used = estimateTokens(messages);
    int budget = contextBudgetPort.contextLength(model);
    int percent = budget <= 0 ? 0 : (int) ((long) used * PERCENT / budget);
    return new ContextUsage(used, budget, percent);
  }

  /**
   * Tells whether the history has reached the compaction threshold.
   *
   * @param usage measured usage
   * @return {@code true} if the history should be compacted before the next request
   */
  public boolean isOverThreshold(ContextUsage usage) {
    return usage.budgetTokens() > 0
        && usage.usedTokens() >= policy.compactThreshold() * usage.budgetTokens();
  }
}
