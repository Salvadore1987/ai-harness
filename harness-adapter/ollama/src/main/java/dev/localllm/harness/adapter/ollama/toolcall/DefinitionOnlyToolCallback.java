package dev.localllm.harness.adapter.ollama.toolcall;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Carries a tool definition to the model without being executable: the harness executes tool
 * calls itself through its tool registry, Spring AI only sends the definitions.
 */
final class DefinitionOnlyToolCallback implements ToolCallback {

  private final ToolDefinition definition;

  DefinitionOnlyToolCallback(ToolDefinition definition) {
    this.definition = definition;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return definition;
  }

  @Override
  public String call(String toolInput) {
    throw new UnsupportedOperationException(
        "Tool " + definition.name() + " is executed by the harness, not by Spring AI");
  }
}
