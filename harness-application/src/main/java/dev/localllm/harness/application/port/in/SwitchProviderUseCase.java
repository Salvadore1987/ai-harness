package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.SwitchProviderCommand;
import dev.localllm.harness.application.dto.result.ProviderResult;

/**
 * Validates and selects another LLM provider.
 */
public interface SwitchProviderUseCase {

  /**
   * Checks that the provider is available and returns the model a new session starts with.
   *
   * @param command target provider
   * @return the selected provider
   * @throws dev.localllm.harness.application.exception.ProviderUnavailableException if the
   *     provider cannot be used
   */
  ProviderResult execute(SwitchProviderCommand command);
}
