package dev.localllm.harness.application.dto.command;

/**
 * Switches the session to another installed model.
 *
 * @param model name of the target model
 */
public record SwitchModelCommand(String model) {
}
