package dev.localllm.harness.application.dto.query;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * Filter for the list of installed models.
 *
 * @param provider provider whose models are listed
 * @param namePrefix only models whose name starts with this prefix; empty for all models
 */
public record ListModelsCriteria(LlmProvider provider, String namePrefix) {
}
