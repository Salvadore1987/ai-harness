package dev.localllm.harness.application.model;

/**
 * Result of compacting a conversation history.
 *
 * @param compacted whether anything was replaced
 * @param summarized {@code true} if the model wrote a summary, {@code false} if the oldest
 *     messages were dropped without one
 * @param tokensBefore estimated tokens before compaction
 * @param tokensAfter estimated tokens after compaction
 */
public record CompactionOutcome(
    boolean compacted,
    boolean summarized,
    int tokensBefore,
    int tokensAfter) {
}
