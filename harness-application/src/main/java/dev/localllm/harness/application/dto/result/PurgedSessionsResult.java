package dev.localllm.harness.application.dto.result;

/**
 * Outcome of deleting stored sessions.
 *
 * @param deletedCount number of deleted sessions
 */
public record PurgedSessionsResult(int deletedCount) {
}
