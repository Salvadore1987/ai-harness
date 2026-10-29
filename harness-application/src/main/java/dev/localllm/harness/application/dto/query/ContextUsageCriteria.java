package dev.localllm.harness.application.dto.query;

import dev.localllm.harness.application.model.SessionId;

/**
 * Identifies the context window to measure.
 *
 * @param sessionId session whose history is measured
 * @param model model whose context window is the budget
 */
public record ContextUsageCriteria(SessionId sessionId, String model) {
}
