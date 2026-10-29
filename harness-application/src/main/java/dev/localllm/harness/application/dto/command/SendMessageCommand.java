package dev.localllm.harness.application.dto.command;

import java.nio.file.Path;

import dev.localllm.harness.application.model.SessionId;

/**
 * Sends a user message and streams the model answer.
 *
 * @param sessionId session the message belongs to
 * @param workingDirectory directory the session works in, stored with the session metadata
 * @param model model that generates the answer
 * @param text user message text
 * @param thinkingEnabled whether the model is asked to reason before answering
 */
public record SendMessageCommand(
    SessionId sessionId,
    Path workingDirectory,
    String model,
    String text,
    boolean thinkingEnabled) {
}
