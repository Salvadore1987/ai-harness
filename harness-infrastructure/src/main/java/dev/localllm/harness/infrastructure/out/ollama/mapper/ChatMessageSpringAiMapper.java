package dev.localllm.harness.infrastructure.out.ollama.mapper;

import java.util.List;
import java.util.Objects;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ToolCall;

/**
 * Converts the core conversation history into Spring AI messages.
 *
 * <p>Tool calls of an assistant message are sent natively; a tool result becomes a
 * {@link ToolResponseMessage}, which Ollama receives as a message with the role {@code tool}.
 */
@Mapper
public interface ChatMessageSpringAiMapper {

  /** Tool call type expected by Spring AI. */
  String FUNCTION_TYPE = "function";

  /**
   * Converts the whole history, keeping the order.
   *
   * @param messages core messages, oldest first
   * @return Spring AI messages, oldest first
   */
  List<Message> toSpringAiMessages(List<ChatMessage> messages);

  /**
   * Converts one message into the Spring AI type matching its role.
   *
   * @param message core message
   * @return Spring AI message
   */
  default Message toSpringAiMessage(ChatMessage message) {
    return switch (message.role()) {
      case SYSTEM -> new SystemMessage(message.content());
      case USER -> new UserMessage(message.content());
      case ASSISTANT -> AssistantMessage.builder()
          .content(message.content())
          .toolCalls(toSpringAiToolCalls(message.toolCalls()))
          .build();
      case TOOL_RESULT -> ToolResponseMessage.builder()
          .responses(List.of(new ToolResponseMessage.ToolResponse(
              Objects.requireNonNullElse(message.toolCallId(), ""),
              Objects.requireNonNullElse(message.toolName(), ""),
              message.content())))
          .build();
    };
  }

  /**
   * Converts the tool calls of an assistant message.
   *
   * @param calls core tool calls
   * @return Spring AI tool calls
   */
  List<AssistantMessage.ToolCall> toSpringAiToolCalls(List<ToolCall> calls);

  /**
   * Converts one tool call.
   *
   * @param call core tool call
   * @return Spring AI tool call
   */
  @Mapping(target = "type", constant = FUNCTION_TYPE)
  @Mapping(target = "arguments", source = "argumentsJson")
  AssistantMessage.ToolCall toSpringAiToolCall(ToolCall call);
}
