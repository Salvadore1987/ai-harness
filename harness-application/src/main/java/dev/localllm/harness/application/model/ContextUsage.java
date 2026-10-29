package dev.localllm.harness.application.model;

/**
 * Estimated fill level of the context window.
 *
 * @param usedTokens estimated tokens of the history sent with the next request
 * @param budgetTokens context window of the loaded model
 * @param percent used share of the window, rounded down
 */
public record ContextUsage(int usedTokens, int budgetTokens, int percent) {
}
