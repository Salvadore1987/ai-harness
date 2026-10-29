package dev.localllm.harness.application.model;

import org.jspecify.annotations.Nullable;

/**
 * Description of a tool offered to the model.
 *
 * @param name tool name, unique across all sources
 * @param description what the tool does, shown to the model
 * @param inputSchema JSON Schema of the arguments
 * @param source where the tool comes from
 * @param server name of the MCP server providing the tool, {@code null} for built-in tools
 */
public record ToolSpec(
    String name,
    String description,
    String inputSchema,
    ToolSource source,
    @Nullable String server) {
}
