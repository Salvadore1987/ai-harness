package dev.localllm.harness.application.dto.result;

import dev.localllm.harness.application.model.ToolSource;

/**
 * An available tool as shown to the user.
 *
 * @param name tool name
 * @param description what the tool does
 * @param source where the tool comes from
 */
public record ToolSummaryResult(String name, String description, ToolSource source) {
}
