package dev.localllm.harness.application.model;

/**
 * Limits that keep the agent loop from running away.
 *
 * @param maxIterations model requests allowed in one agent turn
 * @param maxParseFailures consecutive steps whose calls could not be matched to a tool before
 *     the loop stops
 */
public record AgentPolicy(int maxIterations, int maxParseFailures) {
}
