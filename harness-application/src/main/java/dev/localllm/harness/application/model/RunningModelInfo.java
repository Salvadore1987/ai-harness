package dev.localllm.harness.application.model;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A model currently loaded into memory by the local model server.
 *
 * @param name model tag, for example {@code qwen3.5:27b}
 * @param sizeBytes memory the loaded model takes, in bytes
 * @param vramBytes part of {@code sizeBytes} held in GPU memory
 * @param contextLength context window the model was loaded with; {@code null} if not reported
 * @param expiresAt when the server unloads the model if it stays idle; {@code null} if not
 *     reported
 */
public record RunningModelInfo(
    String name,
    long sizeBytes,
    long vramBytes,
    @Nullable Integer contextLength,
    @Nullable Instant expiresAt) {
}
