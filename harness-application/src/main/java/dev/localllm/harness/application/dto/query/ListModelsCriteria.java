package dev.localllm.harness.application.dto.query;

/**
 * Filter for the list of installed models.
 *
 * @param namePrefix only models whose name starts with this prefix; empty for all models
 */
public record ListModelsCriteria(String namePrefix) {
}
