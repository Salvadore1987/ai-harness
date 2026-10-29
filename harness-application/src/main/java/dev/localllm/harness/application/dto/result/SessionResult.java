package dev.localllm.harness.application.dto.result;

import dev.localllm.harness.application.model.SessionId;

/**
 * A newly started session.
 *
 * @param sessionId identifier of the session
 */
public record SessionResult(SessionId sessionId) {
}
