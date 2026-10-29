package dev.localllm.harness.application.dto.command;

import dev.localllm.harness.application.model.SessionId;

/**
 * Compacts the history of a session on request.
 *
 * @param sessionId session to compact
 * @param model model that writes the summary
 */
public record CompactSessionCommand(SessionId sessionId, String model) {
}
