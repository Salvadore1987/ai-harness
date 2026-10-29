package dev.localllm.harness.application.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Stored metadata of a chat session.
 *
 * @param sessionId identifier of the session
 * @param workingDirectory directory the session was started in
 * @param model model of the first turn
 * @param title beginning of the first user message
 * @param createdAt time of the first turn
 * @param messageCount number of stored messages, including compacted ones
 */
public record SessionInfo(
    SessionId sessionId,
    Path workingDirectory,
    String model,
    String title,
    Instant createdAt,
    int messageCount) {
}
