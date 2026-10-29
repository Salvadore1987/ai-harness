package dev.localllm.harness.application.dto.command;

import java.nio.file.Path;

import dev.localllm.harness.application.model.SessionId;

/**
 * Sends a user message to the agent, which may call tools before answering.
 *
 * @param sessionId session the message belongs to
 * @param workingDirectory directory the tools resolve relative paths against
 * @param model model that drives the agent
 * @param text user message text
 * @param thinkingEnabled whether the model is asked to reason before each step
 */
public record RunAgentTurnCommand(
    SessionId sessionId,
    Path workingDirectory,
    String model,
    String text,
    boolean thinkingEnabled) {
}
