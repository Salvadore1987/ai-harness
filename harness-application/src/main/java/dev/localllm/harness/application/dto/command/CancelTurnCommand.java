package dev.localllm.harness.application.dto.command;

import dev.localllm.harness.application.model.SessionId;

/**
 * Cancels the generation that is currently running.
 *
 * @param sessionId session whose turn is cancelled
 */
public record CancelTurnCommand(SessionId sessionId) {
}
