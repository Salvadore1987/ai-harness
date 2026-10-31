package dev.localllm.harness.application.dto.query;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.SessionId;

/**
 * Identifies the context window to measure.
 *
 * @param sessionId session whose history is measured
 * @param provider provider of the model
 * @param model model whose context window is the budget
 */
public record ContextUsageCriteria(SessionId sessionId, LlmProvider provider, String model) {
}
