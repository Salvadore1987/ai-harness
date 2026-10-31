package dev.localllm.harness.application.dto.command;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * Turns the thinking mode on or off for the current model.
 *
 * @param provider provider of the current model
 * @param model current model
 * @param enabled requested state of the thinking mode
 */
public record ToggleThinkingCommand(LlmProvider provider, String model, boolean enabled) {
}
