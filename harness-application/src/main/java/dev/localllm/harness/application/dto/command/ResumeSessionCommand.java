package dev.localllm.harness.application.dto.command;

import java.nio.file.Path;

/**
 * Continues a stored session.
 *
 * @param workingDirectory directory whose sessions are searched
 * @param reference full session identifier or its unique prefix; empty for the latest session
 */
public record ResumeSessionCommand(Path workingDirectory, String reference) {
}
