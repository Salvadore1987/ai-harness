package dev.localllm.harness.application.dto.command;

/**
 * Turns the thinking mode on or off for the current model.
 *
 * @param model current model
 * @param enabled requested state of the thinking mode
 */
public record ToggleThinkingCommand(String model, boolean enabled) {
}
