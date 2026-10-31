package dev.localllm.harness.application.dto.command;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * Switches the CLI to another LLM provider.
 *
 * @param provider target provider
 */
public record SwitchProviderCommand(LlmProvider provider) {
}
