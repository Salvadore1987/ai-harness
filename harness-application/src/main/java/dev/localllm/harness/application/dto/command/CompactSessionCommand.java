package dev.localllm.harness.application.dto.command;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.SessionId;

/**
 * Compacts the history of a session on request.
 *
 * @param sessionId session to compact
 * @param provider provider of the model
 * @param model model that writes the summary
 */
public record CompactSessionCommand(SessionId sessionId, LlmProvider provider, String model) {
}
