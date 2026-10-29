package dev.localllm.harness.application.dto.result;

/**
 * Estimated fill level of the context window.
 *
 * @param usedTokens estimated tokens of the history
 * @param budgetTokens context window of the model
 * @param percent used share of the window, rounded down
 */
public record ContextUsageResult(int usedTokens, int budgetTokens, int percent) {
}
