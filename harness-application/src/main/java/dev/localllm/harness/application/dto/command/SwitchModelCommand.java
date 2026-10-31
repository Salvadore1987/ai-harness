package dev.localllm.harness.application.dto.command;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * Switches the session to another model of its provider.
 *
 * @param provider provider of the session
 * @param model name of the target model
 */
public record SwitchModelCommand(LlmProvider provider, String model) {
}
