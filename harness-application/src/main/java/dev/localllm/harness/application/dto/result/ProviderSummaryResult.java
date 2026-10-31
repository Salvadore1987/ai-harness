package dev.localllm.harness.application.dto.result;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * A provider as shown to the user.
 *
 * @param provider the provider
 * @param available whether requests can be sent to it
 * @param active whether it is the provider of the current session
 * @param defaultModel model a new session of this provider starts with
 */
public record ProviderSummaryResult(
    LlmProvider provider,
    boolean available,
    boolean active,
    String defaultModel) {
}
