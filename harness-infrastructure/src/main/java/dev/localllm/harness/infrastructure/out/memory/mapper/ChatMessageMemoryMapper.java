package dev.localllm.harness.infrastructure.out.memory.mapper;

import java.util.List;
import java.util.Objects;

import org.mapstruct.Mapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;

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
      case ASSISTANT -> new AssistantMessage(message.content());
    };
  }

  /**
   * Converts a Spring AI message into a core message.
   *
   * @param message Spring AI message
   * @return core message
   */
  default ChatMessage toChatMessage(Message message) {
    Role role = switch (message.getMessageType()) {
      case SYSTEM -> Role.SYSTEM;
      case ASSISTANT -> Role.ASSISTANT;
      default -> Role.USER;
    };
    return new ChatMessage(role, Objects.requireNonNullElse(message.getText(), ""));
  }
}
