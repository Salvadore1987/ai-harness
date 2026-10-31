package dev.localllm.harness.application.dto.command;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.SessionId;

/**
 * Deletes the stored sessions of a directory.
 *
 * @param workingDirectory directory whose sessions are deleted
 * @param currentSessionId session in use, which is kept; {@code null} if none
 */
public record PurgeSessionsCommand(Path workingDirectory, @Nullable SessionId currentSessionId) {
}
