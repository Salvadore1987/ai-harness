package dev.localllm.harness.application.model;

import java.time.Duration;

/**
 * Outcome of one streamed model turn.
 *
 * @param content answer text received so far (complete unless the turn was cut short)
 * @param status how the turn ended
 * @param usage token counters, {@link TokenUsage#EMPTY} if the server sent none
 * @param elapsed wall-clock duration of the turn
 */
public record ChatCompletion(
    String content,
    TurnStatus status,
    TokenUsage usage,
    Duration elapsed) {
}
