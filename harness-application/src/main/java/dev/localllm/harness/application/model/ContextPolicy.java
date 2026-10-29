package dev.localllm.harness.application.model;

/**
 * Rules for keeping the conversation inside the model context window.
 *
 * @param compactThreshold share of the context window that triggers compaction, for example 0.8
 * @param keepTailShare share of the context window that the most recent messages may take and
 *     still be kept verbatim by a compaction, for example 0.25
 * @param charsPerToken average characters per token used for estimates
 */
public record ContextPolicy(double compactThreshold, double keepTailShare, double charsPerToken) {
}
