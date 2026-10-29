package dev.localllm.harness.application.model;

/**
 * Rules for keeping the conversation inside the model context window.
 *
 * @param compactThreshold share of the context window that triggers compaction, for example 0.8
 * @param keepLastMessages messages kept verbatim after compaction
 * @param charsPerToken average characters per token used for estimates
 */
public record ContextPolicy(double compactThreshold, int keepLastMessages, double charsPerToken) {
}
