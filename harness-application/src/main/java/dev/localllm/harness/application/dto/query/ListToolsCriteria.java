package dev.localllm.harness.application.dto.query;

/**
 * Filter for the list of available tools.
 *
 * @param namePrefix only tools whose name starts with this prefix; empty for all tools
 */
public record ListToolsCriteria(String namePrefix) {
}
