package dev.localllm.harness.application.dto.command;

import dev.localllm.harness.application.model.SessionId;

/**
 * Sends a user message and streams the model answer.
 *
 * @param sessionId session the message belongs to
 * @param model model that generates the answer
 * @param text user message text
 * @param thinkingEnabled whether the model is asked to reason before answering
 */
public record SendMessageCommand(
    SessionId sessionId,
    String model,
    String text,
    boolean thinkingEnabled) {
}
