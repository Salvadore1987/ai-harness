package dev.localllm.harness.application.dto.query;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * Describes the list of providers to show.
 *
 * @param activeProvider provider of the current session, marked in the list
 */
public record ListProvidersCriteria(LlmProvider activeProvider) {
}
