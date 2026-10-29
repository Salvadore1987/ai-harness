package dev.localllm.harness.infrastructure.out.ollama.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import dev.localllm.harness.application.model.ChatMessage;

/**
 * Converts the core conversation history into Spring AI messages.
 */
@Mapper
public interface ChatMessageSpringAiMapper {

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
      case ASSISTANT -> new AssistantMessage(message.content());
    };
  }
}
