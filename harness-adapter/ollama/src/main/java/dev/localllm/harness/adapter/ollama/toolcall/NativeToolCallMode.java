package dev.localllm.harness.adapter.ollama.toolcall;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import dev.localllm.harness.adapter.ollama.mapper.ChatMessageSpringAiMapper;
import dev.localllm.harness.adapter.ollama.mapper.ToolCallSpringAiMapper;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSpec;

/**
 * Native tool calling of Ollama: the tool definitions go into the {@code tools} field of the
 * request, the calls come back in {@code message.tool_calls}.
 *
 * <p>Spring AI must not execute the calls: the adapter turns off the auto-registered
 * {@code ToolCallingAdvisor}, and the callbacks passed here only carry definitions.
 */
@Component
public class NativeToolCallMode implements ToolCallMode {

  private final ChatMessageSpringAiMapper messageMapper;
  private final ToolCallSpringAiMapper toolCallMapper;

  /**
   * Creates the mode.
   *
   * @param messageMapper converts the history into Spring AI messages
   * @param toolCallMapper converts tool descriptions and calls
   */
  public NativeToolCallMode(
      ChatMessageSpringAiMapper messageMapper, ToolCallSpringAiMapper toolCallMapper) {
    this.messageMapper = messageMapper;
    this.toolCallMapper = toolCallMapper;
  }

  @Override
  public List<Message> toMessages(ChatRequest request) {
    return messageMapper.toSpringAiMessages(request.messages());
  }

  @Override
  public List<ToolCallback> toolCallbacks(List<ToolSpec> tools) {
    List<ToolCallback> callbacks = new ArrayList<>(tools.size());
    for (ToolSpec tool : tools) {
      callbacks.add(new DefinitionOnlyToolCallback(toolCallMapper.toToolDefinition(tool)));
    }
    return callbacks;
  }

  @Override
  public ToolCallCollector newCollector(List<ToolSpec> tools) {
    return new NativeCollector();
  }

  /**
   * Collects the native calls; Spring AI merges the chunks of a call into one response, so calls
   * are not split across responses. The answer text is shown as it is.
   */
  private final class NativeCollector implements ToolCallCollector {

    private final List<ToolCall> calls = new ArrayList<>();

    @Override
    public String visible(String fragment) {
      return fragment;
    }

    @Override
    public boolean accept(ChatResponse response) {
      int before = calls.size();
      for (Generation generation : response.getResults()) {
        calls.addAll(toolCallMapper.toToolCalls(generation.getOutput().getToolCalls()));
      }
      return calls.size() > before;
    }

    @Override
    public boolean isComplete() {
      return false;
    }

    @Override
    public String flush() {
      return "";
    }

    @Override
    public CollectedToolCalls finish(String content) {
      return new CollectedToolCalls(content, calls, null);
    }
  }
}
