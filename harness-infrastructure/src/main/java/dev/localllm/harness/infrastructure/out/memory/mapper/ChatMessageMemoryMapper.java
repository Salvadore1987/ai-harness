package dev.localllm.harness.infrastructure.out.memory.mapper;

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
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.ToolCall;

/**
 * Converts between core messages and the Spring AI messages kept in chat memory.
 */
@Mapper
public interface ChatMessageMemoryMapper {

  /**
   * Converts a Spring AI history into core messages, keeping the order.
   *
   * @param messages Spring AI messages
   * @return core messages
   */
  List<ChatMessage> toChatMessages(List<Message> messages);

  /**
   * Converts a core message into the Spring AI type matching its role.
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
   * Converts a Spring AI message into a core message.
   *
   * @param message Spring AI message
   * @return core message
   */
  default ChatMessage toChatMessage(Message message) {
    String text = Objects.requireNonNullElse(message.getText(), "");
    return switch (message) {
      case ToolResponseMessage tool when !tool.getResponses().isEmpty() -> {
        ToolResponseMessage.ToolResponse response = tool.getResponses().getFirst();
        yield new ChatMessage(Role.TOOL_RESULT, response.responseData(), List.of(),
            response.id(), response.name());
      }
      case AssistantMessage assistant ->
          ChatMessage.assistant(text, toToolCalls(assistant.getToolCalls()));
      case SystemMessage ignored -> new ChatMessage(Role.SYSTEM, text);
      default -> new ChatMessage(Role.USER, text);
    };
  }

  /**
   * Converts core tool calls into Spring AI tool calls.
   *
   * @param calls core tool calls
   * @return Spring AI tool calls
   */
  List<AssistantMessage.ToolCall> toSpringAiToolCalls(List<ToolCall> calls);

  /**
   * Converts one core tool call.
   *
   * @param call core tool call
   * @return Spring AI tool call
   */
  @Mapping(target = "type", constant = "function")
  @Mapping(target = "arguments", source = "argumentsJson")
  AssistantMessage.ToolCall toSpringAiToolCall(ToolCall call);

  /**
   * Converts Spring AI tool calls into core tool calls.
   *
   * @param calls Spring AI tool calls
   * @return core tool calls
   */
  List<ToolCall> toToolCalls(List<AssistantMessage.ToolCall> calls);

  /**
   * Converts one Spring AI tool call.
   *
   * @param call Spring AI tool call
   * @return core tool call
   */
  @Mapping(target = "argumentsJson", source = "arguments")
  ToolCall toToolCall(AssistantMessage.ToolCall call);
}
