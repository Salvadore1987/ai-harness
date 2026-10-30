package dev.localllm.harness.application.dto.command;

import java.nio.file.Path;

import dev.localllm.harness.application.model.SessionId;

/**
 * Saves a stored answer of the session to a file verbatim, without asking the model again.
 *
 * @param sessionId session whose history holds the answer
 * @param workingDirectory directory a relative path is resolved against
 * @param path target file, relative or absolute; a directory or empty for a generated name
 * @param skipCurrentTurn whether to skip the turn in progress, so that the agent saves the
 *     answer the user was looking at when asking for it
 */
public record SaveAnswerCommand(
    SessionId sessionId,
    Path workingDirectory,
    String path,
    boolean skipCurrentTurn) {
}
