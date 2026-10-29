package dev.localllm.harness.application.model;

/**
 * Description of a tool offered to the model.
 *
 * @param name tool name, unique across all sources
 * @param description what the tool does, shown to the model
 * @param inputSchema JSON Schema of the arguments
 * @param source where the tool comes from
 */
public record ToolSpec(String name, String description, String inputSchema, ToolSource source) {
}
