package dev.localllm.harness.application.dto.command;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.SessionId;

/**
 * Starts a new session, discarding the history of the previous one.
 *
 * @param previousSessionId session being replaced, {@code null} on startup
 */
public record StartSessionCommand(@Nullable SessionId previousSessionId) {
}
