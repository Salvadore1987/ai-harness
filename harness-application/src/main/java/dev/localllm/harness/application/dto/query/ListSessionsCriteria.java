package dev.localllm.harness.application.dto.query;

import java.nio.file.Path;

/**
 * Filter for the list of stored sessions.
 *
 * @param workingDirectory only sessions started in this directory
 */
public record ListSessionsCriteria(Path workingDirectory) {
}
