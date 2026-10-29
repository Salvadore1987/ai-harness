package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ToolCallSpringAiMapper;

/**
 * Native tool calling of Ollama: the tool definitions go into the {@code tools} field of the
 * request, the calls come back in {@code message.tool_calls}.
 *
 * <p>Spring AI must not execute the calls: the adapter turns off the auto-registered
 * {@code ToolCallingAdvisor}, and the callbacks passed here only carry definitions.
 */
@Component
public class NativeToolCallMode {

  private final ToolCallSpringAiMapper mapper;

  /**
   * Creates the mode.
   *
   * @param mapper converts tool descriptions and calls
   */
  public NativeToolCallMode(ToolCallSpringAiMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Builds the callbacks that carry the tool definitions to the model.
   *
   * @param tools tools offered to the model
   * @return definition-only callbacks, empty if no tools are offered
   */
  public List<ToolCallback> toolCallbacks(List<ToolSpec> tools) {
    List<ToolCallback> callbacks = new ArrayList<>(tools.size());
    for (ToolSpec tool : tools) {
      callbacks.add(new DefinitionOnlyToolCallback(mapper.toToolDefinition(tool)));
    }
    return callbacks;
  }

  /**
   * Reads the tool calls of one streamed response. Spring AI merges the chunks of a call into
   * one response, so calls are not split across responses.
   *
   * @param response streamed response
   * @return tool calls, empty if the response carries none
   */
  public List<ToolCall> toolCalls(ChatResponse response) {
    List<ToolCall> calls = new ArrayList<>();
    for (Generation generation : response.getResults()) {
      calls.addAll(mapper.toToolCalls(generation.getOutput().getToolCalls()));
    }
    return calls;
  }
}
