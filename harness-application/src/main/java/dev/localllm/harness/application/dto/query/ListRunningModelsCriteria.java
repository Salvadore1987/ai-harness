package dev.localllm.harness.application.dto.query;

/**
 * Filter for the list of loaded models.
 *
 * @param namePrefix only models whose name starts with this prefix; empty for all models
 */
public record ListRunningModelsCriteria(String namePrefix) {
}
