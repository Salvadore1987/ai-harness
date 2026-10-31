package dev.localllm.harness.adapter.ollama.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.definition.ToolDefinition;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSpec;

/**
 * Converts tool descriptions and native tool calls between the core and Spring AI.
 */
@Mapper
public interface ToolCallSpringAiMapper {

  /** Arguments sent when the model called a tool without any. */
  String EMPTY_ARGUMENTS = "{}";

  /**
   * Converts the tool calls of a model response.
   *
   * @param calls Spring AI tool calls
   * @return core tool calls
   */
  List<ToolCall> toToolCalls(List<AssistantMessage.ToolCall> calls);

  /**
   * Converts one tool call; Ollama often sends calls without an id, so one is generated.
   *
   * @param call Spring AI tool call
   * @return core tool call
   */
  default ToolCall toToolCall(AssistantMessage.ToolCall call) {
    String id = call.id() == null || call.id().isBlank() ? ToolCall.newId() : call.id();
    String arguments = call.arguments() == null || call.arguments().isBlank()
        ? EMPTY_ARGUMENTS : call.arguments();
    return new ToolCall(id, call.name(), arguments);
  }

  /**
   * Converts a tool description into the Spring AI definition sent to the model.
   *
   * @param tool core tool description
   * @return tool definition
   */
  default ToolDefinition toToolDefinition(ToolSpec tool) {
    return ToolDefinition.builder()
        .name(tool.name())
        .description(tool.description())
        .inputSchema(tool.inputSchema())
        .build();
  }
}
