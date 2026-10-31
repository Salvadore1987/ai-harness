package dev.localllm.harness.application.dto.result;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * A provider the CLI has switched to.
 *
 * @param provider the selected provider
 * @param defaultModel model the new session starts with
 */
public record ProviderResult(LlmProvider provider, String defaultModel) {
}
