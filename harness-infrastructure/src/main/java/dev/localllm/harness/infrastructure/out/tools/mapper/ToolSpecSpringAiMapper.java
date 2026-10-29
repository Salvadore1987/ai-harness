package dev.localllm.harness.infrastructure.out.tools.mapper;

import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.springframework.ai.tool.definition.ToolDefinition;

import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;

/**
 * Converts Spring AI tool definitions into core tool descriptions.
 */
@Mapper
public interface ToolSpecSpringAiMapper {

  /**
   * Converts a tool definition.
   *
   * @param definition Spring AI definition
   * @param source where the tool comes from
   * @param server MCP server providing the tool, {@code null} for built-in tools
   * @return core tool description
   */
  default ToolSpec toToolSpec(
      ToolDefinition definition, ToolSource source, @Nullable String server) {
    return new ToolSpec(
        definition.name(), definition.description(), definition.inputSchema(), source, server);
  }
}
