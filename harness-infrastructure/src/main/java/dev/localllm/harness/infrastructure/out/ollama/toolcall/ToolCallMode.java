package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.ToolSpec;

/**
 * How tools are offered to a model and how its tool calls are read back. The history in the
 * core is the same for every mode; a mode only changes how it is sent and how the answer is read.
 */
public interface ToolCallMode {

  /**
   * Converts the history of a request into the messages sent to the model.
   *
   * @param request model, history and offered tools
   * @return Spring AI messages, oldest first
   */
  List<Message> toMessages(ChatRequest request);

  /**
   * Builds the callbacks passed natively in the {@code tools} field of the request.
   *
   * @param tools tools offered to the model
   * @return definition-only callbacks, empty if the mode does not pass tools natively
   */
  List<ToolCallback> toolCallbacks(List<ToolSpec> tools);

  /**
   * Starts reading the tool calls of one streamed answer.
   *
   * @param tools tools offered to the model
   * @return collector for this answer only
   */
  ToolCallCollector newCollector(List<ToolSpec> tools);
}
