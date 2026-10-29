package dev.localllm.harness.application.dto.result;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.ToolSource;

/**
 * An available tool as shown to the user.
 *
 * @param name tool name
 * @param description what the tool does
 * @param source where the tool comes from
 * @param server name of the MCP server providing the tool, {@code null} for built-in tools
 */
public record ToolSummaryResult(
    String name, String description, ToolSource source, @Nullable String server) {
}
