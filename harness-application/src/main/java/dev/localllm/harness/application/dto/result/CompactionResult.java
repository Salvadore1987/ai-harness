package dev.localllm.harness.application.dto.result;

/**
 * Outcome of a history compaction.
 *
 * @param compacted whether anything was replaced
 * @param summarized whether the model wrote a summary or the oldest messages were dropped
 * @param tokensBefore estimated tokens before compaction
 * @param tokensAfter estimated tokens after compaction
 */
public record CompactionResult(
    boolean compacted,
    boolean summarized,
    int tokensBefore,
    int tokensAfter) {
}
