package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.ToolCall;

/**
 * Result of reading a {@code ```tool} block from an answer.
 *
 * @param text answer text without the block if it was read, the whole answer otherwise
 * @param call the call written in the block, {@code null} if there is no block or it is invalid
 * @param error why the block could not be read, {@code null} if there is no block or it is valid
 */
public record ToolBlock(String text, @Nullable ToolCall call, @Nullable String error) {
}
