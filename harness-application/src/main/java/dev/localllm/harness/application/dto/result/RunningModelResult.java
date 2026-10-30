package dev.localllm.harness.application.dto.result;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A loaded model as shown to the user.
 *
 * @param name model tag
 * @param sizeBytes memory the loaded model takes, in bytes
 * @param vramBytes part of {@code sizeBytes} held in GPU memory
 * @param contextLength context window the model was loaded with; {@code null} if unknown
 * @param expiresAt when the model is unloaded if it stays idle; {@code null} if unknown
 */
public record RunningModelResult(
    String name,
    long sizeBytes,
    long vramBytes,
    @Nullable Integer contextLength,
    @Nullable Instant expiresAt) {
}
