package dev.localllm.harness.application.dto.result;

import java.time.Instant;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.SessionId;

/**
 * A stored session as shown to the user.
 *
 * @param sessionId identifier of the session
 * @param title beginning of the first user message
 * @param provider provider of the session
 * @param model model of the first turn
 * @param createdAt time of the first turn
 * @param messageCount number of stored messages
 */
public record SessionSummaryResult(
    SessionId sessionId,
    String title,
    LlmProvider provider,
    String model,
    Instant createdAt,
    int messageCount) {
}
